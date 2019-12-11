package com.sf.doctor;

import org.junit.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TestWhiteList {

    public interface Interface {
        public void run();
    }

    public class Implementation implements Interface {
        @Override
        public void run() {

        }
    }


    @Test
    public void testStrip() {

        String frame = "\"Reference Handler1231\" #2 daemon prio=10 os_prio=0 tid=0x00007f57c4138800 nid=0x6e43c in Object.wait() [0x00007f5755124000]\n" +
                "   java.lang.Thread.State: WAITING (on object monitor)\n" +
                "   JavaThread state: _thread_blocked\n" +
                "Thread: 0x00007f57c4138800  [0x6e43c] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0\n" +
                "   JavaThread state: _thread_blocked\n" +
                "        at java.lang.Object.wait(Native Method)\n" +
                "        at java.lang.Object.wait(Object.java:502)\n" +
                "        at java.lang.ref.Reference.tryHandlePending(Reference.java:191)\n" +
                "        - locked <0x00000000c003e5e8> (a java.lang.ref.Reference$Lock)\n" +
                "        at java.lang.ref.Reference$ReferenceHandler.run(Reference.java:153)";
        frame = frame.replaceAll("0x\\p{XDigit}+", "")
                .replaceAll("=\\d+", "")
                .replaceAll("#\\d+", "#");

        String patter = "(\"[^\"]*\")";

        //System.out.println(frame);
        Matcher matcher = Pattern.compile(patter, Pattern.DOTALL).matcher(frame);
        matcher.find();
        System.out.println(matcher.group(1).replaceAll("\\d+",""));

        ;
        ;
    }


}
