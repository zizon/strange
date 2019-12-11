package com.sf.doctor;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import sun.jvmstat.monitor.MonitorException;
import sun.jvmstat.monitor.MonitoredHost;
import sun.jvmstat.monitor.VmIdentifier;
import sun.tools.attach.HotSpotVirtualMachine;

import java.io.*;
import java.net.InetAddress;
import java.net.StandardSocketOptions;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Strange {

    protected String host;
    protected int port;
    protected ServerSocketChannel channel;

    public Strange() {
        try {
            host = InetAddress.getLocalHost().getHostName();
            channel = ServerSocketChannel.open();
            channel.configureBlocking(true);
            channel.bind(null);
            port = channel.socket().getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException("server creation fail", e);
        }
    }

    public void processIO() {
        try {
            SocketChannel connection = channel.accept();
            channel.close();

            // tuning
            connection.socket().setSoTimeout((int) TimeUnit.SECONDS.toMillis(10));
            connection.shutdownOutput();
            connection.configureBlocking(true);

            ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
            WritableByteChannel console = Channels.newChannel(System.out);

            // use sockt input stream to make timeout effective
            ReadableByteChannel input = Channels.newChannel(connection.socket().getInputStream());
            while (input.read(buffer) != -1) {
                buffer.flip();
                console.write(buffer);
                buffer.compact();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("fail io", e);
        }
    }

    public Strange attach(String pid, String agent, String target) {
        System.out.println(String.format("attatch to %s with agent:%s target:%s", pid, agent, target));

        try {
            System.out.println(String.format("listening at %s:%s", host, port));
            Map<String, String> parameters = new HashMap<>();
            parameters.put("host", host);
            parameters.put("port", Integer.toString(port));
            parameters.put("jar", agent);
            parameters.put("entry", target);

            String arguments = parameters.entrySet().stream()
                    .map((entry) -> String.format("%s=%s", entry.getKey(), entry.getValue()))
                    .collect(Collectors.joining(";"));
            HotSpotVirtualMachine hotspot = (HotSpotVirtualMachine) VirtualMachine.attach(pid);
            hotspot.loadAgent(agent, arguments);

            return this;
        } catch (Throwable throwable) {
            throw new RuntimeException("fail to attach vm", throwable);
        }
    }

    public Optional<String> listVM(VirtualMachineDescriptor vm) {
        HotSpotVirtualMachine hotspot = null;
        try {
            hotspot = (HotSpotVirtualMachine) VirtualMachine.attach(vm.id());

            return Optional.of(
                    Stream.of(
                            Optional.of(String.format(
                                    "Digested Info of VM:%s PID:%s",
                                    vm.displayName(),
                                    vm.id()
                            )),

                            executeCommand(hotspot, "GC.class_histogram",
                                    (lines) -> String.format(
                                            "Top %d Heap Object%n"
                                                    + "%s",
                                            30,
                                            lines.limit(30 + 3)
                                                    .collect(Collectors.joining(System.lineSeparator()))
                                    )
                            ),

                            inspectStack(hotspot),

                            Optional.of(String.format(
                                    "%s%n",
                                    IntStream.range(0, 40)
                                            .mapToObj((ignore) -> "=")
                                            .collect(Collectors.joining())
                            ))
                    ).filter(Optional::isPresent)
                            .map(Optional::get)
                            .collect(Collectors.joining(System.lineSeparator()))
            );
        } catch (AttachNotSupportedException | IOException e) {
            return Optional.empty();
        } finally {
            Optional.ofNullable(hotspot).ifPresent((hotspot_vm) -> {
                try {
                    hotspot_vm.detach();
                } catch (IOException e) {
                    // silence
                }
            });
        }
    }

    protected Optional<String> parseFrame(String frame) {
        frame = frame.replaceAll("0x\\p{XDigit}+", "")
                .replaceAll("=\\d+", "")
                .replaceAll("#\\d+", "#");
        Matcher matcher = Pattern.compile("(\"[^\"]*\")").matcher(frame);
        if (matcher.find()) {
            String origin = matcher.group(1);
            frame = frame.replace(origin, String.format(
                    "%s similar=__COUNTER__",
                    origin.replaceAll("\\d+","")
            ));
        }

        return Optional.of(frame);
    }

    protected Optional<String> inspectStack(HotSpotVirtualMachine hotspot) {
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(hotspot.executeJCmd("Thread.print")))) {
                return Optional.of(String.format(
                        "Grouped Stack%n"
                                + "%s",
                        Arrays.stream(
                                reader.lines()
                                        // skip header
                                        .skip(3)
                                        .collect(Collectors.joining(System.lineSeparator()))
                                        .split(String.format("%n%n"))
                        ).map(this::parseFrame)
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .collect(Collectors.groupingBy(Function.identity(), Collectors.summingInt((drop) -> 1)))
                                .entrySet().stream()
                                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                                .map((entry) -> entry.getKey().replace("__COUNTER__", entry.getValue().toString()))
                                .filter((frame)->frame.contains("similar"))
                                .collect(Collectors.joining(String.format("%n%n")))
                ));
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    protected Optional<String> executeCommand(HotSpotVirtualMachine hotspot, String command, Function<Stream<String>, String> line_consumer) {
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(hotspot.executeJCmd(command)))) {
                return Optional.ofNullable(line_consumer)
                        .map((consumer) -> consumer.apply(reader.lines()));
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static void main(String[] args) throws URISyntaxException {
        switch (args.length) {
            case 0:
                VirtualMachine.list().parallelStream()
                        .map((descriptor) -> {
                            try {
                                VirtualMachine.attach(descriptor).detach();
                                return Optional.of(descriptor);
                            } catch (AttachNotSupportedException | IOException e) {
                                return Optional.<VirtualMachineDescriptor>empty();
                            }
                        })
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map((descriptor) -> String.format(
                                "available VM: %s PID: %s",
                                descriptor.displayName(),
                                descriptor.id()
                        ))
                        .forEach(System.out::println);
                break;

            default:
            case 1:
                switch (args[0]) {
                    case "-h":
                    case "-help":
                    case "help":
                        System.out.println(String.format(
                                "Usage java -jar $jar [ [pid] method]%n"
                                        + "\texample:%n"
                                        + "\t\tlist all accessible vm information%n"
                                        + "\t\t\tjava -jar $jar%n"
                                        + "\t\tlist specified vm information%n"
                                        + "\t\t\tjava -jar $jar $pid%n"
                                        + "\t\tprofile methods of specified class%n"
                                        + "\t\t\tjava -jar $jar $pid org.apache.spark.deploy.history.HistoryServer#*%n"
                                        + "\t\tprofile method of specified class%n"
                                        + "\t\t\tjava -jar $jar $pid org.apache.spark.deploy.history.HistoryServer#getApplicationInfo%n"
                                        + "\t\tprint counters%n"
                                        + "\t\t\tjava -jar $jar $pid stat%n"
                        ));
                        break;
                    default:
                        VirtualMachine.list().parallelStream()
                                .filter((vm) -> vm.id().equals(args[0]))
                                .map((vm) -> new Strange().listVM(vm))
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .forEach(System.out::println)
                        ;
                }

                break;
            case 2:
                if (!args[1].equals("stat")) {
                    new Strange().attach(
                            args[0],
                            new File(Strange.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath(),
                            args[1]
                    ).processIO();
                    break;
                }

                VirtualMachine.list().parallelStream()
                        .filter((descriptor) -> descriptor.id().equals(args[0]))
                        .flatMap((descriptor) -> {
                            try {
                                VmIdentifier vmid = new VmIdentifier(descriptor.id());
                                return MonitoredHost
                                        .getMonitoredHost(vmid)
                                        .getMonitoredVm(vmid)
                                        .findByPattern(".*")
                                        .stream();
                            } catch (URISyntaxException | MonitorException e) {
                                throw new RuntimeException(
                                        String.format(
                                                "fail to create VmIdentifier for:%s",
                                                descriptor
                                        )
                                );
                            }
                        })
                        .map((monitor) -> String.format(
                                "Type:%s  %s = %s",
                                monitor.getUnits(),
                                monitor.getName(),
                                monitor.getValue()
                        ))
                        .forEach(System.out::println);
                break;
        }
    }
}
