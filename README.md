# Concept
For most Java Insturment base Profiler, like [greys](https://github.com/oldmanpushcart/greys-anatomy), provides functionality to trace or account programe executions. 

In daily troubleshooting procedure, one would maily start with monitoring program execution patten, find out interested methods, and then step down/in to those methods, and repeat above. Until a problematic being identified, like a most costly/hot path method that incur performance most.

Take greys for example.

Assume we had a programe with running slowly.
1.  use greys to attach to running jvm.
2.  monitor suspected methods, to find out the costly one.
3.  strack trace the call stack of methods in 3.
4.  break down the stack trace,and take the hottest code to step back 2.
5.  stop untile we are sure everything is clear.

To simplify/do it automatictly, give a method metioned in 3., one can find nested method calls and doing invocation cost accounting.

By recursively apply this accounting, a stack down cost map can be infer from this statistics.

This is the exact basic idea of such project.

# Impelemtation
Said, one wanted to break down costs of `org.apache.spark.deploy.history.HistoryServer getProviderConfig`.

This Agent will first insturment the bytecode of `#getProviderConfig`, transforming
```java
public getProviderConfig(){
   // various codes
}
```
into
```java
public getProviderConfig(){
   // signature is the signature use to identify method,
   // in stack
   Bridge.enter(signature);
   // various codes
   Bridge.leave(signature);
}
```
The`Bridge.enter` marks the start time of execution,and `Bridge.leave`, the end time.

So that in other place, one could counting execution time spent for this particular method.

Then, by `crawling` the `various codes` part, and find method invocations.

Apply insturment the method founded, one could build a full method trace with timeing information.

With a bit stack fiiltering and matching, a full path time sheet table is now buildable.

# Usage
## Tracing
Take, still `org.apache.spark.deploy.history.HistoryServer getProviderConfig` for example.
```shell
// * means to trace all decleared method of Class, 
// or getProviderConfig for certain method.
// strange.jar is the built jar
// and pid is the target running jvm pid
java -jar ${strange.jar} ${pid} org.apache.spark.deploy.history.HistoryServer#*
```
will output something like
```
+---------------+----------------+---------------+------------+------------------------------------------+
|  Average (ns) |   Maximum (ns) |  Minimum (ns) | Invocation | Method                                   |
+---------------+----------------+---------------+------------+------------------------------------------+
| 36,687,945.00 | 131,921,371.00 |  4,159,299.00 |          4 | org/apache/spark/deploy/history/HistoryServer -> getProviderConfig; ()Lscala/collection/immutable/Map; |
+---------------+----------------+---------------+------------+------------------------------------------+
| 33,439,257.25 | 121,298,349.00 |  3,526,112.00 |          4 | org/apache/hadoop/hdfs/DistributedFileSystem -> setSafeMode; (Lorg/apache/hadoop/hdfs/protocol/HdfsConstants$SafeModeAction;Z)Z |
+---------------+----------------+---------------+------------+------------------------------------------+
| 32,168,327.25 | 116,579,702.00 |  3,452,903.00 |          4 | com/sun/proxy/$Proxy11 -> setSafeMode; (Lorg/apache/hadoop/hdfs/protocol/HdfsConstants$SafeModeAction;Z)Z |
+---------------+----------------+---------------+------------+------------------------------------------+
| 31,424,517.00 | 114,060,376.00 |  3,339,497.00 |          4 | org/apache/hadoop/hdfs/protocolPB/ClientNamenodeProtocolTranslatorPB -> setSafeMode; (Lorg/apache/hadoop/hdfs/protocol/HdfsConstants$SafeModeAction;Z)Z |
+---------------+----------------+---------------+------------+------------------------------------------+
| 16,061,288.75 |  64,026,654.00 |     39,875.00 |          4 | org/apache/hadoop/hdfs/protocol/proto/ClientNamenodeProtocolProtos$SetSafeModeRequestProto -> newBuilder; ()Lorg/apache/hadoop/hdfs/protocol/proto/ClientNamenodeProtocolProtos$SetSafeModeRequestProto$Builder; |
+---------------+----------------+---------------+------------+------------------------------------------+
| 16,019,064.50 |  63,873,878.00 |     35,407.00 |          4 | org/apache/hadoop/hdfs/protocol/proto/ClientNamenodeProtocolProtos$SetSafeModeRequestProto$Builder -> access$57000; ()Lorg/apache/hadoop/hdfs/protocol/proto/ClientNamenodeProtocolProtos$SetSafeModeRequestProto$Builder; |
+---------------+----------------+---------------+------------+------------------------------------------+
| 15,948,050.25 |  63,605,891.00 |     31,507.00 |          4 | org/apache/hadoop/hdfs/protocol/proto/ClientNamenodeProtocolProtos$SetSafeModeRequestProto$Builder -> create; ()Lorg/apache/hadoop/hdfs/protocol/proto/ClientNamenodeProtocolProtos$SetSafeModeRequestProto$Builder; |
+---------------+----------------+---------------+------------+------------------------------------------+
| 15,765,746.50 |  62,961,561.00 |     10,416.00 |          4 | org/apache/hadoop/hdfs/protocol/proto/ClientNamenodeProtocolProtos$SetSafeModeRequestProto$Builder -> <init>; ()V |
+---------------+----------------+---------------+------------+------------------------------------------+
| 15,699,502.25 |  62,712,780.00 |      6,077.00 |          4 | org/apache/hadoop/hdfs/protocol/proto/ClientNamenodeProtocolProtos$SetSafeModeRequestProto$Builder -> maybeForceBuilderInitialization; ()V |
+---------------+----------------+---------------+------------+------------------------------------------+
| 14,868,394.80 |  73,744,126.00 |    117,153.00 |          5 | org/apache/spark/deploy/history/HistoryServer -> getApplicationList; ()Lscala/collection/Iterator; |
+---------------+----------------+---------------+------------+------------------------------------------+
| 13,333,259.80 |  66,232,651.00 |     74,724.00 |          5 | scala/collection/AbstractMap -> values; ()Lscala/collection/Iterable; |
+---------------+----------------+---------------+------------+------------------------------------------+
| 12,952,842.80 |  64,353,263.00 |     68,323.00 |          5 | scala/collection/MapLike$class -> values; (Lscala/collection/MapLike;)Lscala/collection/Iterable; |
+---------------+----------------+---------------+------------+------------------------------------------+
| 11,212,163.00 |  11,212,163.00 | 11,212,163.00 |          1 | org/apache/spark/deploy/history/HistoryServer -> getApplicationInfoList; ()Lscala/collection/Iterator; |
+---------------+----------------+---------------+------------+------------------------------------------+
| 10,105,467.00 |  29,381,748.00 |  3,180,324.00 |          4 | org/apache/hadoop/ipc/ProtobufRpcEngine$Invoker -> invoke; (Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object; |
+---------------+----------------+---------------+------------+------------------------------------------+
|  5,534,595.00 |   5,534,595.00 |  5,534,595.00 |          1 | scala/collection/AbstractIterator -> map; (Lscala/Function1;)Lscala/collection/Iterator; |
+---------------+----------------+---------------+------------+------------------------------------------+
|  5,056,875.75 |  20,016,287.00 |     62,700.00 |          4 | org/apache/hadoop/hdfs/protocol/proto/ClientNamenodeProtocolProtos$SetSafeModeRequestProto$Builder -> build; ()Lorg/apache/hadoop/hdfs/protocol/proto/ClientNamenodeProtocolProtos$SetSafeModeRequestProto; |
+---------------+----------------+---------------+------------+------------------------------------------+
|  4,994,114.75 |  19,818,327.00 |     44,232.00 |          4 | org/apache/hadoop/hdfs/protocol/proto/ClientNamenodeProtocolProtos$SetSafeModeRequestProto$Builder -> buildPartial; ()Lorg/apache/hadoop/hdfs/protocol/proto/ClientNamenodeProtocolProtos$SetSafeModeRequestProto; |
+---------------+----------------+---------------+------------+------------------------------------------+
|  4,901,767.46 |  63,292,226.00 |     15,605.00 |         13 | scala/collection/AbstractTraversable -> <init>; ()V |
+---------------+----------------+---------------+------------+------------------------------------------+
|  4,769,291.25 |  19,071,778.00 |      1,636.00 |          4 | org/apache/hadoop/hdfs/protocol/proto/ClientNamenodeProtocolProtos$SetSafeModeRequestProto -> access$57502; (Lorg/apache/hadoop/hdfs/protocol/proto/ClientNamenodeProtocolProtos$SetSafeModeRequestProto;Z)Z |
+---------------+----------------+---------------+------------+------------------------------------------+
|  4,702,341.25 |  18,021,174.00 |    225,888.00 |          4 | org/apache/hadoop/ipc/ProtobufRpcEngine$Invoker -> constructRpcRequestHeader; (Ljava/lang/reflect/Method;)Lorg/apache/hadoop/ipc/protobuf/ProtobufRpcEngineProtos$RequestHeaderProto; |
+---------------+----------------+---------------+------------+------------------------------------------+
|  4,102,035.50 |   8,230,526.00 |  2,378,426.00 |          4 | org/apache/hadoop/ipc/Client -> call; (Lorg/apache/hadoop/ipc/RPC$RpcKind;Lorg/apache/hadoop/io/Writable;Lorg/apache/hadoop/ipc/Client$ConnectionId;Ljava/util/concurrent/atomic/AtomicBoolean;)Lorg/apache/hadoop/io/Writable; |
+---------------+----------------+---------------+------------+------------------------------------------+
|  4,080,431.50 |   8,171,584.00 |  2,369,910.00 |          4 | org/apache/hadoop/ipc/Client -> call; (Lorg/apache/hadoop/ipc/RPC$RpcKind;Lorg/apache/hadoop/io/Writable;Lorg/apache/hadoop/ipc/Client$ConnectionId;ILjava/util/concurrent/atomic/AtomicBoolean;)Lorg/apache/hadoop/io/Writable; |
+---------------+----------------+---------------+------------+------------------------------------------+
```
listing all method invocation stats during/under specified method.

## Listing Attatchable VM
```shell
java -jar ${strange.jar} 
```
Ouputs like
```
available VM: org.apache.spark.deploy.history.HistoryServer PID: 277217
available VM: /tmp/strange-1.0-SNAPSHOT.jar PID: 359655
```

## Stat
```shell
java -jar ${strange.jar} $pid
```
will do thinks like jstack(grouping similar stacktrace) and jmap -histo(showing only top 30)
```
Top 30 Heap Object

 num     #instances         #bytes  class name
----------------------------------------------
   1:         37023        4817112  [C
   2:         12220        1183904  [Ljava.lang.Object;
   3:          8466         942704  java.lang.Class
   4:         36572         877728  java.lang.String
   5:          1665         671208  [B
   6:         17835         570720  java.util.concurrent.ConcurrentHashMap$Node
   7:          4072         238232  [I
   8:         13659         218544  java.lang.Object
   9:           100         210656  [Ljava.util.concurrent.ConcurrentHashMap$Node;
  10:          1640         146304  [Ljava.util.HashMap$Node;
  11:          4509         144288  java.util.HashMap$Node
  12:          1173         103224  java.lang.reflect.Method
  13:          3154         100928  java.util.Hashtable$Entry
  14:          2058          99976  [Ljava.lang.String;
  15:          1284          71904  java.util.LinkedHashMap
  16:             2          65568  [Lcom.alibaba.fastjson.util.IdentityHashMap$Entry;
  17:          1582          63280  java.util.LinkedHashMap$Entry
  18:            16          53528  [Ljava.nio.ByteBuffer;
  19:          1296          51840  com.google.common.collect.MapMakerInternalMap$WeakEntry
  20:          1028          49344  java.util.HashMap
  21:          1070          42800  java.lang.ref.SoftReference
  22:           895          35800  java.util.WeakHashMap$Entry
  23:          1029          32928  java.util.LinkedList
  24:           138          32376  [Z
  25:           400          32000  java.lang.reflect.Constructor
  26:          1297          31128  java.util.ArrayList
  27:           475          30400  java.net.URL
  28:          1217          29208  java.util.LinkedList$Node
  29:            59          27248  [Ljava.util.Hashtable$Entry;
  30:          1083          25624  [Ljava.lang.Class;
Grouped Stack
"GC task thread# (ParallelGC)" similar=23 os_prio tid= nid= runnable

"C CompilerThread" similar=15 # daemon prio os_prio tid= nid= waiting on condition []
   java.lang.Thread.State: RUNNABLE
   JavaThread state: _thread_blocked
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_blocked

"log-replay-executor-" similar=8 # daemon prio os_prio tid= nid= waiting on condition []
   java.lang.Thread.State: WAITING (parking)
   JavaThread state: _thread_blocked
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_blocked
        at sun.misc.Unsafe.park(Native Method)
        - parking to wait for  <> (a java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject)
        at java.util.concurrent.locks.LockSupport.park(LockSupport.java:175)
        at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.await(AbstractQueuedSynchronizer.java:2039)
        at java.util.concurrent.LinkedBlockingQueue.take(LinkedBlockingQueue.java:442)
        at java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.java:1067)
        at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1127)
        at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
        at java.lang.Thread.run(Thread.java:745)

"qtp-" similar=3 # daemon prio os_prio tid= nid= runnable []
   java.lang.Thread.State: RUNNABLE
   JavaThread state: _thread_in_native
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_in_native
        at sun.nio.ch.EPollArrayWrapper.epollWait(Native Method)
        at sun.nio.ch.EPollArrayWrapper.poll(EPollArrayWrapper.java:269)
        at sun.nio.ch.EPollSelectorImpl.doSelect(EPollSelectorImpl.java:93)
        at sun.nio.ch.SelectorImpl.lockAndDoSelect(SelectorImpl.java:86)
        - locked <> (a sun.nio.ch.Util$2)
        - locked <> (a java.util.Collections$UnmodifiableSet)
        - locked <> (a sun.nio.ch.EPollSelectorImpl)
        at sun.nio.ch.SelectorImpl.select(SelectorImpl.java:97)
        at sun.nio.ch.SelectorImpl.select(SelectorImpl.java:101)
        at org.spark_project.jetty.io.ManagedSelector$SelectorProducer.select(Redefined)
        at org.spark_project.jetty.io.ManagedSelector$SelectorProducer.produce(Redefined)
        at org.spark_project.jetty.util.thread.strategy.ExecuteProduceConsume.executeProduceConsume(Redefined)
        at org.spark_project.jetty.util.thread.strategy.ExecuteProduceConsume.produceConsume(Redefined)
        at org.spark_project.jetty.util.thread.strategy.ExecuteProduceConsume.execute(Redefined)
        at org.spark_project.jetty.io.ManagedSelector.run(Redefined)
        at org.spark_project.jetty.util.thread.QueuedThreadPool.runJob(QueuedThreadPool.java:671)
        at org.spark_project.jetty.util.thread.QueuedThreadPool$2.run(Redefined)
        at java.lang.Thread.run(Thread.java:745)

"VM Thread" similar=1 os_prio tid= nid= runnable

"qtp-" similar=1 # daemon prio os_prio tid= nid= waiting on condition []
   java.lang.Thread.State: TIMED_WAITING (parking)
   JavaThread state: _thread_blocked
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_blocked
        at sun.misc.Unsafe.park(Native Method)
        - parking to wait for  <> (a java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject)
        at java.util.concurrent.locks.LockSupport.parkNanos(LockSupport.java:215)
        at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.awaitNanos(AbstractQueuedSynchronizer.java:2078)
        at org.spark_project.jetty.util.BlockingArrayQueue.poll(BlockingArrayQueue.java:392)
        at org.spark_project.jetty.util.thread.QueuedThreadPool.idleJobPoll(QueuedThreadPool.java:563)
        at org.spark_project.jetty.util.thread.QueuedThreadPool.access$800(QueuedThreadPool.java:48)
        at org.spark_project.jetty.util.thread.QueuedThreadPool$2.run(QueuedThreadPool.java:626)
        at java.lang.Thread.run(Thread.java:745)

"LeaseRenewer:root@test-cluster-log" similar=1 # daemon prio os_prio tid= nid= waiting on condition []
   java.lang.Thread.State: TIMED_WAITING (sleeping)
   JavaThread state: _thread_blocked
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_blocked
        at java.lang.Thread.sleep(Native Method)
        at org.apache.hadoop.hdfs.LeaseRenewer.run(LeaseRenewer.java:444)
        at org.apache.hadoop.hdfs.LeaseRenewer.access$700(LeaseRenewer.java:71)
        at org.apache.hadoop.hdfs.LeaseRenewer$1.run(LeaseRenewer.java:304)
        at java.lang.Thread.run(Thread.java:745)

"qtp--acceptor-@ee-ServerConnector@dcaa{HTTP/.,[http/.]}{...:}" similar=1 # daemon prio os_prio tid= nid= waiting for monitor entry []
   java.lang.Thread.State: BLOCKED (on object monitor)
   JavaThread state: _thread_blocked
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_blocked
        at sun.nio.ch.ServerSocketChannelImpl.accept(ServerSocketChannelImpl.java:234)
        - waiting to lock <> (a java.lang.Object)
        at org.spark_project.jetty.server.ServerConnector.accept(Redefined)
        at org.spark_project.jetty.server.AbstractConnector$Acceptor.run(AbstractConnector.java:593)
        at org.spark_project.jetty.util.thread.QueuedThreadPool.runJob(QueuedThreadPool.java:671)
        at org.spark_project.jetty.util.thread.QueuedThreadPool$2.run(Redefined)
        at java.lang.Thread.run(Thread.java:745)

"qtp--acceptor-@dcf-ServerConnector@dcaa{HTTP/.,[http/.]}{...:}" similar=1 # daemon prio os_prio tid= nid= waiting for monitor entry []
   java.lang.Thread.State: BLOCKED (on object monitor)
   JavaThread state: _thread_blocked
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_blocked
        at sun.nio.ch.ServerSocketChannelImpl.accept(ServerSocketChannelImpl.java:234)
        - waiting to lock <> (a java.lang.Object)
        at org.spark_project.jetty.server.ServerConnector.accept(Redefined)
        at org.spark_project.jetty.server.AbstractConnector$Acceptor.run(AbstractConnector.java:593)
        at org.spark_project.jetty.util.thread.QueuedThreadPool.runJob(QueuedThreadPool.java:671)
        at org.spark_project.jetty.util.thread.QueuedThreadPool$2.run(Redefined)
        at java.lang.Thread.run(Thread.java:745)

"spark-history-task-" similar=1 # daemon prio os_prio tid= nid= waiting on condition []
   java.lang.Thread.State: TIMED_WAITING (parking)
   JavaThread state: _thread_blocked
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_blocked
        at sun.misc.Unsafe.park(Native Method)
        - parking to wait for  <> (a java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject)
        at java.util.concurrent.locks.LockSupport.parkNanos(LockSupport.java:215)
        at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.awaitNanos(AbstractQueuedSynchronizer.java:2078)
        at java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take(ScheduledThreadPoolExecutor.java:1093)
        at java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take(ScheduledThreadPoolExecutor.java:809)
        at java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.java:1067)
        at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1127)
        at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
        at java.lang.Thread.run(Thread.java:745)

"qtp--acceptor-@fe-ServerConnector@dcaa{HTTP/.,[http/.]}{...:}" similar=1 # daemon prio os_prio tid= nid= runnable []
   java.lang.Thread.State: RUNNABLE
   JavaThread state: _thread_in_native
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_in_native
        at sun.nio.ch.ServerSocketChannelImpl.accept0(Native Method)
        at sun.nio.ch.ServerSocketChannelImpl.accept(ServerSocketChannelImpl.java:422)
        at sun.nio.ch.ServerSocketChannelImpl.accept(ServerSocketChannelImpl.java:250)
        - locked <> (a java.lang.Object)
        at org.spark_project.jetty.server.ServerConnector.accept(Redefined)
        at org.spark_project.jetty.server.AbstractConnector$Acceptor.run(AbstractConnector.java:593)
        at org.spark_project.jetty.util.thread.QueuedThreadPool.runJob(QueuedThreadPool.java:671)
        at org.spark_project.jetty.util.thread.QueuedThreadPool$2.run(Redefined)
        at java.lang.Thread.run(Thread.java:745)

"Attach Listener" similar=1 # daemon prio os_prio tid= nid= waiting on condition []
   java.lang.Thread.State: RUNNABLE
   JavaThread state: _thread_blocked
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_blocked

"qtp--acceptor-@fe-ServerConnector@dcaa{HTTP/.,[http/.]}{...:}" similar=1 # daemon prio os_prio tid= nid= waiting for monitor entry []
   java.lang.Thread.State: BLOCKED (on object monitor)
   JavaThread state: _thread_blocked
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_blocked
        at sun.nio.ch.ServerSocketChannelImpl.accept(ServerSocketChannelImpl.java:234)
        - waiting to lock <> (a java.lang.Object)
        at org.spark_project.jetty.server.ServerConnector.accept(Redefined)
        at org.spark_project.jetty.server.AbstractConnector$Acceptor.run(AbstractConnector.java:593)
        at org.spark_project.jetty.util.thread.QueuedThreadPool.runJob(QueuedThreadPool.java:671)
        at org.spark_project.jetty.util.thread.QueuedThreadPool$2.run(Redefined)
        at java.lang.Thread.run(Thread.java:745)

"Signal Dispatcher" similar=1 # daemon prio os_prio tid= nid= runnable []
   java.lang.Thread.State: RUNNABLE
   JavaThread state: _thread_blocked
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_blocked

"Finalizer" similar=1 # daemon prio os_prio tid= nid= in Object.wait() []
   java.lang.Thread.State: WAITING (on object monitor)
   JavaThread state: _thread_blocked
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_blocked
        at java.lang.Object.wait(Native Method)
        at java.lang.ref.ReferenceQueue.remove(ReferenceQueue.java:143)
        - locked <> (a java.lang.ref.ReferenceQueue$Lock)
        at java.lang.ref.ReferenceQueue.remove(ReferenceQueue.java:164)
        at java.lang.ref.Finalizer$FinalizerThread.run(Finalizer.java:209)

"VM Periodic Task Thread" similar=1 os_prio tid= nid= waiting on condition

"-JettyScheduler" similar=1 # daemon prio os_prio tid= nid= waiting on condition []
   java.lang.Thread.State: WAITING (parking)
   JavaThread state: _thread_blocked
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_blocked
        at sun.misc.Unsafe.park(Native Method)
        - parking to wait for  <> (a java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject)
        at java.util.concurrent.locks.LockSupport.park(LockSupport.java:175)
        at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.await(AbstractQueuedSynchronizer.java:2039)
        at java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take(ScheduledThreadPoolExecutor.java:1081)
        at java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take(ScheduledThreadPoolExecutor.java:809)
        at java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.java:1067)
        at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1127)
        at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
        at java.lang.Thread.run(Thread.java:745)

"Service Thread" similar=1 # daemon prio os_prio tid= nid= runnable []
   java.lang.Thread.State: RUNNABLE
   JavaThread state: _thread_blocked
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_blocked

"main" similar=1 # prio os_prio tid= nid= waiting on condition []
   java.lang.Thread.State: TIMED_WAITING (sleeping)
   JavaThread state: _thread_blocked
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_blocked
        at java.lang.Thread.sleep(Native Method)
        at org.apache.spark.deploy.history.HistoryServer$.main(HistoryServer.scala:289)
        at org.apache.spark.deploy.history.HistoryServer.main(Redefined)

"IPC Parameter Sending Thread #" similar=1 # daemon prio os_prio tid= nid= waiting on condition []
   java.lang.Thread.State: TIMED_WAITING (parking)
   JavaThread state: _thread_blocked
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_blocked
        at sun.misc.Unsafe.park(Native Method)
        - parking to wait for  <> (a java.util.concurrent.SynchronousQueue$TransferStack)
        at java.util.concurrent.locks.LockSupport.parkNanos(LockSupport.java:215)
        at java.util.concurrent.SynchronousQueue$TransferStack.awaitFulfill(SynchronousQueue.java:460)
        at java.util.concurrent.SynchronousQueue$TransferStack.transfer(SynchronousQueue.java:362)
        at java.util.concurrent.SynchronousQueue.poll(SynchronousQueue.java:941)
        at java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.java:1066)
        at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1127)
        at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
        at java.lang.Thread.run(Thread.java:745)

"org.apache.hadoop.fs.FileSystem$Statistics$StatisticsDataReferenceCleaner" similar=1 # daemon prio os_prio tid= nid= in Object.wait() []
   java.lang.Thread.State: WAITING (on object monitor)
   JavaThread state: _thread_blocked
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_blocked
        at java.lang.Object.wait(Native Method)
        - waiting on <> (a java.lang.ref.ReferenceQueue$Lock)
        at java.lang.ref.ReferenceQueue.remove(ReferenceQueue.java:143)
        - locked <> (a java.lang.ref.ReferenceQueue$Lock)
        at java.lang.ref.ReferenceQueue.remove(ReferenceQueue.java:164)
        at org.apache.hadoop.fs.FileSystem$Statistics$StatisticsDataReferenceCleaner.run(Redefined)
        at java.lang.Thread.run(Thread.java:745)

"IPC Client () connection to /...: from root" similar=1 # daemon prio os_prio tid= nid= in Object.wait() []
   java.lang.Thread.State: TIMED_WAITING (on object monitor)
   JavaThread state: _thread_blocked
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_blocked
        at java.lang.Object.wait(Native Method)
        at org.apache.hadoop.ipc.Client$Connection.waitForWork(Client.java:933)
        - locked <> (a org.apache.hadoop.ipc.Client$Connection)
        at org.apache.hadoop.ipc.Client$Connection.run(Client.java:978)

"Reference Handler" similar=1 # daemon prio os_prio tid= nid= in Object.wait() []
   java.lang.Thread.State: WAITING (on object monitor)
   JavaThread state: _thread_blocked
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_blocked
        at java.lang.Object.wait(Native Method)
        at java.lang.Object.wait(Object.java:502)
        at java.lang.ref.Reference.tryHandlePending(Reference.java:191)
        - locked <> (a java.lang.ref.Reference$Lock)
        at java.lang.ref.Reference$ReferenceHandler.run(Reference.java:153)

"qtp-" similar=1 # daemon prio os_prio tid= nid= runnable []
   java.lang.Thread.State: RUNNABLE
   JavaThread state: _thread_in_native
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_in_native
        at sun.nio.ch.EPollArrayWrapper.epollWait(Native Method)
        at sun.nio.ch.EPollArrayWrapper.poll(EPollArrayWrapper.java:269)
        at sun.nio.ch.EPollSelectorImpl.doSelect(EPollSelectorImpl.java:93)
        at sun.nio.ch.SelectorImpl.lockAndDoSelect(SelectorImpl.java:86)
        - locked <> (a sun.nio.ch.Util$2)
        - locked <> (a java.util.Collections$UnmodifiableSet)
        - locked <> (a sun.nio.ch.EPollSelectorImpl)
        at sun.nio.ch.SelectorImpl.select(SelectorImpl.java:97)
        at sun.nio.ch.SelectorImpl.select(SelectorImpl.java:101)
        at org.spark_project.jetty.io.ManagedSelector$SelectorProducer.select(Redefined)
        at org.spark_project.jetty.io.ManagedSelector$SelectorProducer.produce(Redefined)
        at org.spark_project.jetty.util.thread.strategy.ExecuteProduceConsume.executeProduceConsume(Redefined)
        at org.spark_project.jetty.util.thread.strategy.ExecuteProduceConsume.produceConsume(Redefined)
        at org.spark_project.jetty.util.thread.strategy.ExecuteProduceConsume.run(Redefined)
        at org.spark_project.jetty.util.thread.QueuedThreadPool.runJob(QueuedThreadPool.java:671)
        at org.spark_project.jetty.util.thread.QueuedThreadPool$2.run(Redefined)
        at java.lang.Thread.run(Thread.java:745)

"org.apache.hadoop.hdfs.PeerCache@cb" similar=1 # daemon prio os_prio tid= nid= waiting on condition []
   java.lang.Thread.State: TIMED_WAITING (sleeping)
   JavaThread state: _thread_blocked
Thread:   [] State: _at_safepoint _has_called_back 0 _at_poll_safepoint 0
   JavaThread state: _thread_blocked
        at java.lang.Thread.sleep(Native Method)
        at org.apache.hadoop.hdfs.PeerCache.run(Redefined)
        at org.apache.hadoop.hdfs.PeerCache.access$000(Redefined)
        at org.apache.hadoop.hdfs.PeerCache$1.run(Redefined)
        at java.lang.Thread.run(Thread.java:745)
========================================
```

## Counters
```shell
java -jar ${strange.jar} $pid stat
```
outpus some counters of JVM runtime
```
Type:Ticks  java.ci.totalTime = 1020162085885
Type:Events  java.cls.loadedClasses = 18025
Type:Events  java.cls.sharedLoadedClasses = 0
Type:Events  java.cls.sharedUnloadedClasses = 0
Type:Events  java.cls.unloadedClasses = 274
Type:String  java.property.java.class.path = /app/spark/conf/:/app/spark/jars/janino-3.0.0.jar:/app/spark/jars/spark-tags_2.11-2.2.0.jar:/app/spark/jars/javax.inject-1.jar:/app/spark/jars/spark-repl_2.11-2.2.0.jar:/app/spark/jars/leveldbjni-all-1.8.jar:/app/spark/jars/jackson-core-2.6.5.jar:/app/spark/jars/libfb303-0.9.3.jar:/app/spark/jars/jackson-mapper-asl-1.9.13.jar:/app/spark/jars/minlog-1.3.0.jar:/app/spark/jars/base64-2.3.8.jar:/app/spark/jars/htrace-core-3.1.0-incubating.jar:/app/spark/jars/jackson-databind-2.6.5.jar:/app/spark/jars/hadoop-yarn-common-2.7.3.jar:/app/spark/jars/aopalliance-repackaged-2.4.0-b34.jar:/app/spark/jars/api-util-1.0.0-M20.jar:/app/spark/jars/jcl-over-slf4j-1.7.16.jar:/app/spark/jars/core-1.1.2.jar:/app/spark/jars/commons-httpclient-3.1.jar:/app/spark/jars/hadoop-yarn-client-2.7.3.jar:/app/spark/jars/mx4j-3.0.2.jar:/app/spark/jars/kryo-shaded-3.0.3.jar:/app/spark/jars/spark-mllib_2.11-2.2.0.jar:/app/spark/jars/antlr4-runtime-4.5.3.jar:/app/spark/jars/javax.ws.rs-api-2.0.1.jar:/app/spark/jars/commons-crypto-1.0.0.jar:/app
Type:String  java.property.java.endorsed.dirs = /app/jdk8u/build/linux-x86_64-normal-server-fastdebug/jdk/lib/endorsed
Type:String  java.property.java.ext.dirs = /app/jdk8u/build/linux-x86_64-normal-server-fastdebug/jdk/lib/ext:/usr/java/packages/lib/ext
Type:String  java.property.java.home = /app/jdk8u/build/linux-x86_64-normal-server-fastdebug/jdk
Type:String  java.property.java.library.path = /usr/lib/oracle/11.2/client64/lib::/usr/java/packages/lib/amd64:/usr/lib64:/lib64:/lib:/usr/lib
Type:String  java.property.java.version = 1.8.0-internal-fastdebug
Type:String  java.property.java.vm.info = mixed mode
Type:String  java.property.java.vm.name = OpenJDK 64-Bit Server VM
Type:String  java.property.java.vm.specification.name = Java Virtual Machine Specification
Type:String  java.property.java.vm.specification.vendor = Oracle Corporation
Type:String  java.property.java.vm.specification.version = 1.8
Type:String  java.property.java.vm.vendor = Oracle Corporation
Type:String  java.property.java.vm.version = 25.71-b00-fastdebug
Type:String  java.rt.vmArgs = -Xmx1g
Type:String  java.rt.vmFlags =
Type:None  java.threads.daemon = 28
Type:None  java.threads.live = 29
Type:None  java.threads.livePeak = 33
Type:Events  java.threads.started = 889
Type:Events  sun.ci.compilerThread.0.compiles = 223
Type:String  sun.ci.compilerThread.0.method =
Type:Ticks  sun.ci.compilerThread.0.time = 69784
Type:None  sun.ci.compilerThread.0.type = 1
Type:Events  sun.ci.compilerThread.1.compiles = 231
Type:String  sun.ci.compilerThread.1.method =
Type:Ticks  sun.ci.compilerThread.1.time = 231262
Type:None  sun.ci.compilerThread.1.type = 1
Type:Events  sun.ci.compilerThread.10.compiles = 1309
Type:String  sun.ci.compilerThread.10.method =
Type:Ticks  sun.ci.compilerThread.10.time = 557587
Type:None  sun.ci.compilerThread.10.type = 1
Type:Events  sun.ci.compilerThread.11.compiles = 1321
Type:String  sun.ci.compilerThread.11.method =
Type:Ticks  sun.ci.compilerThread.11.time = 609030
Type:None  sun.ci.compilerThread.11.type = 1
Type:Events  sun.ci.compilerThread.12.compiles = 1432
Type:String  sun.ci.compilerThread.12.method =
Type:Ticks  sun.ci.compilerThread.12.time = 534446
Type:None  sun.ci.compilerThread.12.type = 1
Type:Events  sun.ci.compilerThread.13.compiles = 1315
Type:String  sun.ci.compilerThread.13.method =
Type:Ticks  sun.ci.compilerThread.13.time = 533086
Type:None  sun.ci.compilerThread.13.type = 1
Type:Events  sun.ci.compilerThread.14.compiles = 1295
Type:String  sun.ci.compilerThread.14.method =
Type:Ticks  sun.ci.compilerThread.14.time = 594089
Type:None  sun.ci.compilerThread.14.type = 1
Type:Events  sun.ci.compilerThread.2.compiles = 225
Type:String  sun.ci.compilerThread.2.method =
Type:Ticks  sun.ci.compilerThread.2.time = 167667
Type:None  sun.ci.compilerThread.2.type = 1
Type:Events  sun.ci.compilerThread.3.compiles = 262
Type:String  sun.ci.compilerThread.3.method =
Type:Ticks  sun.ci.compilerThread.3.time = 140532
Type:None  sun.ci.compilerThread.3.type = 1
Type:Events  sun.ci.compilerThread.4.compiles = 223
Type:String  sun.ci.compilerThread.4.method =
Type:Ticks  sun.ci.compilerThread.4.time = 142069
Type:None  sun.ci.compilerThread.4.type = 1
Type:Events  sun.ci.compilerThread.5.compiles = 183
Type:String  sun.ci.compilerThread.5.method =
Type:Ticks  sun.ci.compilerThread.5.time = 135220
Type:None  sun.ci.compilerThread.5.type = 1
Type:Events  sun.ci.compilerThread.6.compiles = 246
Type:String  sun.ci.compilerThread.6.method =
Type:Ticks  sun.ci.compilerThread.6.time = 211373
Type:None  sun.ci.compilerThread.6.type = 1
Type:Events  sun.ci.compilerThread.7.compiles = 232
Type:String  sun.ci.compilerThread.7.method =
Type:Ticks  sun.ci.compilerThread.7.time = 159669
Type:None  sun.ci.compilerThread.7.type = 1
Type:Events  sun.ci.compilerThread.8.compiles = 215
Type:String  sun.ci.compilerThread.8.method =
Type:Ticks  sun.ci.compilerThread.8.time = 220982
Type:None  sun.ci.compilerThread.8.type = 1
Type:Events  sun.ci.compilerThread.9.compiles = 202
Type:String  sun.ci.compilerThread.9.method =
Type:Ticks  sun.ci.compilerThread.9.time = 147255
Type:None  sun.ci.compilerThread.9.type = 1
Type:String  sun.ci.lastFailedMethod = java/io/ExpiringCache put
Type:None  sun.ci.lastFailedType = 1
Type:String  sun.ci.lastInvalidatedMethod =
Type:None  sun.ci.lastInvalidatedType = 0
Type:String  sun.ci.lastMethod = java/util/concurrent/ScheduledThreadPoolExecutor$DelayedWorkQueue finishPoll
Type:Bytes  sun.ci.lastSize = 60
Type:None  sun.ci.lastType = 1
Type:Bytes  sun.ci.nmethodCodeSize = 54408544
Type:Bytes  sun.ci.nmethodSize = 85616344
Type:Bytes  sun.ci.osrBytes = 118750
Type:Events  sun.ci.osrCompiles = 77
Type:Ticks  sun.ci.osrTime = 100528818297
Type:Bytes  sun.ci.standardBytes = 2781709
Type:Events  sun.ci.standardCompiles = 8832
Type:Ticks  sun.ci.standardTime = 919633267588
Type:Bytes  sun.ci.threads = 15
Type:Events  sun.ci.totalBailouts = 5
Type:Events  sun.ci.totalCompiles = 8909
Type:Events  sun.ci.totalInvalidates = 0
Type:None  sun.classloader.findClassTime = 19230192507
Type:None  sun.classloader.findClasses = 6113
Type:None  sun.classloader.parentDelegationTime = 672034923
Type:Bytes  sun.cls.appClassBytes = 27271940
Type:Events  sun.cls.appClassLoadCount = 7814
Type:Ticks  sun.cls.appClassLoadTime = 19452372340
Type:Ticks  sun.cls.appClassLoadTime.self = 4098420114
Type:Ticks  sun.cls.classInitTime = 6015228107
Type:Ticks  sun.cls.classInitTime.self = 1061367881
Type:Ticks  sun.cls.classLinkedTime = 10434125462
Type:Ticks  sun.cls.classLinkedTime.self = 2060023552
Type:Ticks  sun.cls.classVerifyTime = 8372758792
Type:Ticks  sun.cls.classVerifyTime.self = 1371893132
Type:Ticks  sun.cls.defineAppClassTime = 15031981965
Type:Ticks  sun.cls.defineAppClassTime.self = 11600126092
Type:Ticks  sun.cls.defineAppClasses = 6139
Type:Events  sun.cls.initializedClasses = 5330
Type:Events  sun.cls.isUnsyncloadClassSet = 0
Type:Events  sun.cls.jniDefineClassNoLockCalls = 44
Type:Events  sun.cls.jvmDefineClassNoLockCalls = 6059
Type:Events  sun.cls.jvmFindLoadedClassNoLockCalls = 14145
Type:Events  sun.cls.linkedClasses = 8611
Type:Events  sun.cls.loadInstanceClassFailRate = 0
Type:Bytes  sun.cls.loadedBytes = 39388208
Type:Ticks  sun.cls.lookupSysClassTime = 109678475
Type:Bytes  sun.cls.methodBytes = 21457536
Type:Events  sun.cls.nonSystemLoaderLockContentionRate = 3
Type:Ticks  sun.cls.parseClassTime = 170494327897
Type:Ticks  sun.cls.parseClassTime.self = 168633720389
Type:Ticks  sun.cls.sharedClassLoadTime = 1307102
Type:Bytes  sun.cls.sharedLoadedBytes = 0
Type:Bytes  sun.cls.sharedUnloadedBytes = 0
Type:Bytes  sun.cls.sysClassBytes = 6487460
Type:Ticks  sun.cls.sysClassLoadTime = 688918739
Type:Events  sun.cls.systemLoaderLockContentionRate = 0
Type:Ticks  sun.cls.time = 189647029764
Type:Bytes  sun.cls.unloadedBytes = 481688
Type:Events  sun.cls.unsafeDefineClassCalls = 280
Type:Events  sun.cls.verifiedClasses = 7755
Type:String  sun.gc.cause = No GC
Type:Events  sun.gc.collector.0.invocations = 317
Type:Ticks  sun.gc.collector.0.lastEntryTime = 5576788828340
Type:Ticks  sun.gc.collector.0.lastExitTime = 5576842414274
Type:String  sun.gc.collector.0.name = PSScavenge
Type:Ticks  sun.gc.collector.0.time = 20754707746
Type:Events  sun.gc.collector.1.invocations = 5
Type:Ticks  sun.gc.collector.1.lastEntryTime = 5388031782117
Type:Ticks  sun.gc.collector.1.lastExitTime = 5475215352044
Type:String  sun.gc.collector.1.name = PSParallelCompact
Type:Ticks  sun.gc.collector.1.time = 171378389970
Type:Bytes  sun.gc.compressedclassspace.capacity = 11272192
Type:Bytes  sun.gc.compressedclassspace.maxCapacity = 1073741824
Type:Bytes  sun.gc.compressedclassspace.minCapacity = 0
Type:Bytes  sun.gc.compressedclassspace.used = 10883008
Type:Bytes  sun.gc.generation.0.capacity = 277348352
Type:Bytes  sun.gc.generation.0.maxCapacity = 357564416
Type:Bytes  sun.gc.generation.0.minCapacity = 357564416
Type:String  sun.gc.generation.0.name = new
Type:Bytes  sun.gc.generation.0.space.0.capacity = 260571136
Type:Bytes  sun.gc.generation.0.space.0.initCapacity = 0
Type:Bytes  sun.gc.generation.0.space.0.maxCapacity = 356515840
Type:String  sun.gc.generation.0.space.0.name = eden
Type:Bytes  sun.gc.generation.0.space.0.used = 31120680
Type:Bytes  sun.gc.generation.0.space.1.capacity = 7864320
Type:Bytes  sun.gc.generation.0.space.1.initCapacity = 0
Type:Bytes  sun.gc.generation.0.space.1.maxCapacity = 119013376
Type:String  sun.gc.generation.0.space.1.name = s0
Type:Bytes  sun.gc.generation.0.space.1.used = 0
Type:Bytes  sun.gc.generation.0.space.2.capacity = 7864320
Type:Bytes  sun.gc.generation.0.space.2.initCapacity = 0
Type:Bytes  sun.gc.generation.0.space.2.maxCapacity = 119013376
Type:String  sun.gc.generation.0.space.2.name = s1
Type:Bytes  sun.gc.generation.0.space.2.used = 4653056
Type:None  sun.gc.generation.0.spaces = 3
Type:Bytes  sun.gc.generation.1.capacity = 716177408
Type:Bytes  sun.gc.generation.1.maxCapacity = 716177408
Type:Bytes  sun.gc.generation.1.minCapacity = 716177408
Type:String  sun.gc.generation.1.name = old
Type:Bytes  sun.gc.generation.1.space.0.capacity = 716177408
Type:Bytes  sun.gc.generation.1.space.0.initCapacity = 716177408
Type:Bytes  sun.gc.generation.1.space.0.maxCapacity = 716177408
Type:String  sun.gc.generation.1.space.0.name = old
Type:Bytes  sun.gc.generation.1.space.0.used = 12946696
Type:None  sun.gc.generation.1.spaces = 1
Type:String  sun.gc.lastCause = Allocation Failure
Type:Bytes  sun.gc.metaspace.capacity = 120807424
Type:Bytes  sun.gc.metaspace.maxCapacity = 1184890880
Type:Bytes  sun.gc.metaspace.minCapacity = 0
Type:Bytes  sun.gc.metaspace.used = 117640104
Type:Bytes  sun.gc.policy.avgBaseFootprint = 268435456
Type:Ticks  sun.gc.policy.avgMajorIntervalTime = 1163009
Type:Ticks  sun.gc.policy.avgMajorPauseTime = 37533
```
