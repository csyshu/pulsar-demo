# Pulsar架构和概念

## 一、概要

Pulsar最初由yahoo开发，是一个多租户、高可用，服务间的消息系统。现在由Apache Software Foundation管理。

Pulsar的主要特性如下；

- Pulsar通过跨集群的消息无缝geo-replication，让Pulsar实例原生支持多集群
- 很低的发布和端到端延迟
- 无缝的可扩展性，乃至上百万个topic
- 简洁的client API，支持Java、Python、C++
- 多种topic的订阅模式（独占、共享、灾备）
- 使用Apache BookKeeper，确保传递的消息被持久存储
- serverless轻量级计算框架Pulsar Function，提供流原生数据处理。
- serverless connector框架Pulsar IO。它构建于Pulsar Function之上，使得从Apache Pulsar移入和移出数据更为简单
- 分层存储把过期的数据从热活存储区转移到冷/长期存储区（比如S3和GCS）

## 二、架构

在最高层，Pulsar实例由一个或多个Pulsar集群组成。同一个实例内的集群间可以相互复制数据，

Pulsar集群由下面三部分组成：

- 一个或者多个broker负责处理和负载均衡源源不断从生产者发送出的消息，并将他们发送给消费者。它与配置存储交互来处理相应的任务，将消息存储在BookKeeper实例中(也称为bookies)。它依赖ZooKeeper集群处理特定的任务，等等。
- 由一个或多个bookie组成的BookKeeper集群，处理消息的持久存储
- 用于集群操作的Zookeeper集群

下图是Pulsar集群的说明：

![img](https://img-blog.csdnimg.cn/20181105133617877.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2xpeWltaW5nMjAxNw==,size_16,color_FFFFFF,t_70)

在更大的实例层面来看，实例范围的ZooKeeper集群，负责配置管理，处理多集群间的协调工作。例如，geo-replication。

### 1、Broker 

Pulsar消息broker是无状态的组件，主要作用是运行另外两个组件：

- HTTP服务器，通过REST API来实现管理任务，以及为producer和consumer提供[topic查询 ](http://pulsar.apache.org/docs/en/concepts-clients/#client-setup-phase)
- 调度器，异步TCP服务，通过客制化的[binary protocol](http://pulsar.apache.org/docs/en/develop-binary-protocol)处理所有数据的传输

出于性能的考虑，消息通常被分发到[托管ledger](http://pulsar.apache.org/docs/en/concepts-architecture-overview/#managed-ledgers)缓存，除非backlog已经超出了缓存的大小。如果backlog对于缓存来说增长的太大，broker将开始从BookKeepr读取数据条目。

最后，为了支持全局topic的geo-replication，broker使用复制器查询出发布在本区域尾部的条目，然后通过[Java客户端类库](http://pulsar.apache.org/docs/en/client-libraries-java/)重新把他们发步到远程地域上。

> Pulsar broker的管理指南，请参考[broker](http://pulsar.apache.org/docs/en/admin-api-brokers)向导

### 2、Cluster

Pulsar的实例，由一到多个Pulsar集群组成。而集群组成如下：

- 一到多个Pulsar broker
- ZooKeeper仲裁，作为集群层面的配置和协调
- 一套bookie，用作来消息的持久存储。

集群可以通过geo-replication相互复制。

> Pulsar cluster的管理指南，请参考[cluster](http://pulsar.apache.org/docs/en/admin-api-clusters/)向导

### 3、元数据存储

Pulsar使用Apache ZooKeeper存储元数据、集群配置数据、协同数据。在Pulsar实例中：

- 配置存储仲裁存储了租户的配置信息、namespace，以及其他所有需要全局一致的信息条目。
- 每个集群有他自己本地的一套ZooKeeper，存储了集群指定的配置和协调信息，如元数据的所有权、broker负载报告、BookKeeper ledger元数据等等。

### 4、持久存储

Pulsar为应用提供消息发送的保证。一旦消息成功到达Pulsar broker，他将会被发送到他预期的目标上。

这种保证，需要消息被发送及确认前，被持久存储。这种消息传输的模式，通常被称为持久化消息传输。在Pulsar中，所有消息的N份拷贝被存储及同步在硬盘上，例如，跨越两台server的4份拷贝，存储于镜像[RAID](https://en.wikipedia.org/wiki/RAID)卷。

### 5、Apcahe BookKeeper

Pulsar使用Apache BookKeeper系统来持久存储消息。BookKeeper是分布式 [write-ahead log](https://en.wikipedia.org/wiki/Write-ahead_logging) (WAL) 系统，为Pulsar提供了很重要的优势：

- 它使得Pulsar可以使用很多称为legder的独立log。随着时间的移动，可以为topic创建很多的ledger。
- 它为有序数据提供了非常高效的存储，并处理数据条目复制。
- 在各种可能的系统崩溃下，它可以保证ledger的读一致性。
- 它提供跨bookie的I/O分配。
- 它在容量及吞吐量上都能做到可扩展。可以通过为集群增加bookie立即提升容量。
- Bookie的设计支持处理成千上万ledger的读写并发。通过使用多个硬盘设备---一块用于journal，其他的用做普通存储，这把读操作从正在进行的写操作延迟影响中隔离出来。

对于消息数据，还有游标也被持久存储在BookKeeper。游标是消费者订阅的位置。BookKeeper使Pulsar可以用流行的可扩展方式存储consumer的位置。

在Pulsar仅支持消息持久存储的时候，所有的topic名称中都有persistent描述。下面是一个例子：

```
persistent://my-tenant/my-namespace/my-topic
```

> Pulsar也支持临时（非持久）消息存储。 

下图展示了broker和bookie是如何交互的：

![img](https://img-blog.csdnimg.cn/20181105133918612.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2xpeWltaW5nMjAxNw==,size_16,color_FFFFFF,t_70)

### 6、Ledger

ledger是单写入仅可追加的数据结构，被分配给多个BookKeeper的存储节点或者bookie。Ledger条目被复制到多个bookie。Ledge自己的语义十分简单：

- Pulsar broker可以创建ledger，往ledger添加条目，关闭ledger。
- ledger关闭后--无论是主动还是因为写入程序错误---他都可以被以只读的方式打开。
- 最后，当ledger中的条目不再需要时，整个ledger都将从系统中被删除（跨所有bookie）

**Ledger的读一致性**

BookKeeper最主要的长处是他在存在失败的情况下，能够保证ledger的读一致性。因为ledger仅可被单线程写入，这让程序很轻易就做到高效增加条目，而不需要考虑一致性。失败后，ledger将会执行恢复程序，最终确定ledger状态并发布最后提交的条目日志。经过上述操作，ledger的所有读取者会被保证读取到完全一样的内容。、

**托管ledger**

考虑到BookKeeper Ledger提供单一log抽象，构建于ledger之上的类库，被称为托管ledger，它代表了单一log的存储层。托管ledger代表消息流的抽象。它有一个单独的写入者，往流的末尾持续添加数据，多个游标在消费流数据，每个游标都持有自己的关联位置。

在内部，一个单独的托管ledger使用多个BookKeeper ledger来存储数据。使用多个ledger出于以下两个原因考虑：

1. 失败后，ledger不再可写，一个新的ledger需要被创建
2. 当ledger含有的全部消息被所有cursor都消费完成时，ledger就可以被删除了。这允许ledger周期回滚。

### 7、Journal存储

BookKeeper中，journal文件包含了BookKeeper的事务日志。在对ledger更新前，bookie需要确认描述更新的事务已经被持久保存。一旦bookie开启或者老的journal文件达到了journal文件大小的阈值（通过journalMaxSizeMB 配置），新的journal文件将被创建。 

### 8、Pulsar代理

直接连接到Pulsar的broker上，是Pulsar客户端和pulsar集群交互的一个方法。然而在某些情况下，这种直连方式不但不可行也是不被希望的，因为客户端并没有直连broker的地址。如果你在云环境下运行Pulsar或者Kubernetes或者类似的平台，客户端直连broker看上去并不可行。

Pulsar代理对此问题提供了一个方案，通过作为集群中所有broker的单一网关。如果你运行Pulsar代理（可选），所有Pulsar集群的客户端连接都将通过代理，而不是和broker直接通信。

> 出于性能和容错的考虑，你可以运行你需要的的Pulsar代理实例数量

 关于架构，Pulsar代理从ZooKeeper获取它所需要的所有信息。当你在机器上启动代理，你需要提供ZooKeeper的连接串来指定集群以及实例范围的配置存储集群。示例如下：

```bash
$ bin/pulsar proxy \



  --zookeeper-servers zk-0,zk-1,zk-2 \



  --configuration-store-servers zk-0,zk-1,zk-2
```

> Publsar代理文档
>
> Pulsar代理的相关使用文档，请参考[Pulsar proxy admin documentation](http://pulsar.apache.org/docs/en/administration-proxy).

关于Pulsar代理需要知道的要点：

- 连接的客户端不需要提供任何指定配置来使用Pulsar代理。除了更新服务URL的IP，你并不需要为现有程序更新客户端配置（例如你正在通过Pulsar代理运行负载均衡）。
- Pulsar代理支持[TLS encryption](http://pulsar.apache.org/docs/en/security-tls-transport) 和 [authentication](http://pulsar.apache.org/docs/en/security-tls-authentication)

##  

### 9、服务发现

连接到Pulsar broker的客户端需要能通过单个URL和整个Pulsar实例通信。Pulsar提供了内置的服务发现机制，你可以按照[部署Pulsar实例](http://pulsar.apache.org/docs/en/deploy-bare-metal/#service-discovery-setup)指南中的说明来构建此机制。

你可以按你的需要来使用自己的服务发现系统。如果你使用自己的系统，仅需要做一件事：当客户端执行一个HTTP请求，例如http://pulsar.us-west.example.com:8080，客户端需要被重定向到目标集群某个活跃的broker上，无论通过DNS，HTTP还是IP重定向，或者其他的手段。

下图说明了Pulsar的服务发现：

![img](https://img-blog.csdnimg.cn/20181105134424458.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2xpeWltaW5nMjAxNw==,size_16,color_FFFFFF,t_70)

此图中，Pulsar集群通过DNS名称pulsar-cluster.acme.com被寻址。例如在Python客户端中，可以通过如下方式进入这个Pulsar集群：

```python
from pulsar import Client

client = Client('pulsar://pulsar-cluster.acme.com:6650')
```

 

## 三、关于消息

Pular采用了发布订阅的设计模式，也称作pub-sub。producer发布消息到topic，consumer可以订阅这些topic，处理发布过来的消息，在处理完成后发送确认。

一旦订阅被创建，所有的消息都将被Pulsar保存，即使consumer已断开了连接。只有在consumer确认消息已经被成功处理后，保存下来的消息才会被丢弃。

### 1、消息

消息是Pulsar的基础单元。producer发给topic的内容，consumer从topic消费的内容，就是消息。和邮政系统类比，消息就相当于信件。

| 组成               | 用途                                                         |
| ------------------ | ------------------------------------------------------------ |
| value/data payload | 消息携带的数据，所有pulsar的消息携带原始bytes，但是消息数据也需要遵循数据shcema |
| Key                | 消息可以被Key打标签。这可以对topic压缩之类的事情起作用       |
| properties         | 可选的，用户定义属性 的key/value map                         |
| Producer Name      | 生产消息的producer的名称（producer被自动赋予默认名称，但你也可以请求自己指定） |
| Sequence ID        | 在topic中，每个Pulsar消息属于一个有序的序列。消息的sequence ID是他在序列中的次序 |
| Publish time       | 消息发布的时间戳                                             |
| Event time         | 可选的时间戳，应用可以附在消息上，代表某个事件发生的时间，例如，消息被处理时。如果没有 明确的设置，那么event time为0 |

若想了解Pulsar消息内容的更深入分解，请参考Pulasr的[binary protocol](http://pulsar.apache.org/docs/en/develop-binary-protocol)文档

### 2、生产者

生产者是关联到topic的程序，它发布消息到Pulsar的broker上。

#### 2.1、发送模式

producer可以以同步或者异步的方式发布消息到broker。

| 模式     | 描述                                                         |
| -------- | ------------------------------------------------------------ |
| 同步发送 | 发送消息后，producer等待broker的确认。如果没有收到确认，producer会认为发送失败。 |
| 异步发送 | producer将会把消息放入blocking队列，然后马上返回。客户端类库将会在背后把消息发送给broker。 如果队列满了，根据传给producer的参数，producer可能阻塞或者直接返回失败。 |

#### 2.2、压缩

为了节省带宽，在传输过程中，producer发布的消息可以被压缩。目前pulsar支持两种压缩类型：

- [LZ4](https://github.com/lz4/lz4)
- [ZLIB](https://zlib.net/)

#### 2.3、批处理

如果批处理开启，producer将会累积一批消息，然后通过一次请求发送出去。批处理的大小取决于最大的消息数量及最大的发布延迟。

### 3、消费者

消费者通过订阅关联到主题，然后接收消息的程序。

#### 3.1、接收模式

消息可以通过同步或者异步的方式从broker接受。

| 模式     | 描述                                                         |
| -------- | ------------------------------------------------------------ |
| 同步接收 | 同步接收将会阻塞，直到消息可用                               |
| 异步接收 | 异步接收立即返回future值--java中的CompletableFuture，一旦新消息可用，他即刻完成 |

#### 3.2、消息确认

消费者成功处理了消息，需要发送确认给broker，以让broker丢掉这条消息（否则它将存储着此消息）。

消息的确认可以一个接一个，也可以累积一起。累积确认时，消费者只需要确认最后一条他收到的消息。所有之前（包含此条）的消息，都不会被重新发给那个消费者。

> 累积消息确认不能用于shared 订阅模式，因为shared订阅为同一个订阅引入了多个消费者。

#### 3.3 监听

客户端类库提供了他们对于consumer的监听实现。举一个Java客户端的例子，它提供了MessageListener接口。在这个接口中，一旦接受到新的消息，received方法将被调用。

### 4、主题（Topic）

和其他的发布订阅系统一样，Pulsar中的topic是带有名称的通道，用来从producer到consumer传输消息。Topic的名称是符合良好结构的URL。

```
{persistent|non-persistent}://tenant/namespace/topic
```

| Topic名称组成              | 描述                                                         |
| -------------------------- | ------------------------------------------------------------ |
| persistent/ non-persistent | 定义了topic类型，Pulsar支持两种不同topic：持久和非持久 （默认是持久类型，如果你没有指明类型，topic将会是持久类型）。持久topic的所有消息都会保存在硬盘上 （这意味着多块硬盘，除非是单机模式的broker），反之，非持久topic的数据不会存储到硬盘上 |
| tenant                     | 实例中topic的租户。tenant是Pulsar多租户的基本要素。可以被跨集群的传播。 |
| namespace                  | topic的管理单元，相关topic组的管理机制。大多数的topic配置在namespace层面生效。 每个tenant可以有多个namespace |
| topic                      | 主题名称的最后组成部分，topic的名称很自由，没有什么特殊的含义。 |

`不需要显式的创建topic`

> 你并不需要显式的创建topic。如果客户端尝试从一个还不存在的topic写或者接受消息，pulsar将会按在topic名称提供的namnespace下自动创建topic。

### 5、命名空间（namespace）

> 命名空间是租户内部逻辑上的命名术语。一个租户可以通过admin API创建多个命名空间。例如，一个对接多个应用的租户，可以为每个应用创建不同的namespace。

###  6、订阅模型

订阅是命名过的配置规则，指导消息如何投递给消费者。Pulsar有三种订阅模式：exclusive，shared，failover，下图展示了这三种模式：

![img](https://img-blog.csdn.net/20181008111727713?watermark/2/text/aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2xpeWltaW5nMjAxNw==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70)

#### 6.1、Exclusive模型（独占）

独占模式，只能有一个消费者绑定到订阅（subscription）上。如果多于一个消费者尝试以同样方式去订阅主题，消费者将会收到错误。

上面的图中，只有Consumer A可以消费。该模式为默认订阅模式。

![img](https://img-blog.csdn.net/20181008111825696?watermark/2/text/aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2xpeWltaW5nMjAxNw==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70)

#### 6.2、Shared模式（共享）

shared或者*round robin*模式中，多个消费者可以绑定到同一个订阅上。消息通过round robin轮询机制分发给不同的消费者，并且每个消息仅会被分发给一个消费者。当消费者断开连接，所有被发送给他，但没有被确认的消息将被重新安排，分发给其它存活的消费者。

第一幅图中，Consumer-B-1和Consumer-B-2都可以订阅主题，其实Consumer-C-1或者其它Consumer也可以订阅。

> ##### Shared模式的限制
>
> 使用shared模式时，需要重点注意以下两点：
>
> - 消息的顺序无法保证
> - 你不可以使用累积确认

![img](https://img-blog.csdn.net/20181008111901697?watermark/2/text/aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2xpeWltaW5nMjAxNw==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70)

#### 6.3、Failover模式（灾备）

Failover模式中，多个consumer可以绑定到同一个subscription。consumer将会按字典顺序排序，第一个consumer被初始化为唯一接受消息的消费者。这个consumer被称为master consumer。

当master consumer断开时，所有的消息（未被确认和后续进入的）将会被分发给队列中的下一个consumer。

第一个图中，Consumer-C-1是master consumer，当Consumer-C-1断开连接时，由于Consumer-C-2在队列中下一个位置，那么它将会开始接收消息。

![img](https://img-blog.csdn.net/2018100811200322?watermark/2/text/aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2xpeWltaW5nMjAxNw==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70)

### 7、多主题订阅

当consumer订阅pulsar的主题，默认情况下，它订阅了一个指定的主题，例如：persistent://public/default/my-topic。从Pulsar的1.23.0-incubating的版本，Pulsar消费者可以同时订阅多个topic。你可以用以下两种方式定义topic的列表：

- 通过基础的正则表达式（regex），例如 persistent://public/default/finance-.*
- 通过明确定义的topic列表

> 通过正则订阅多主题时，所有的主题必须在同一个命名空间（namespace）

当订阅多主题时，pulsar客户端会自动调用Pulsar的API来发现匹配表达式的所有topic，然后全部订阅。如果此时有暂不存在的topic，那么一旦这些topic被创建，conusmer会自动订阅。

> ##### 不能保证顺序性
>
> 当消费者订阅多主题时，pulsar所提供对单一主题订阅的顺序保证，就hold不住了。如果你在使用pulsar的时候，遇到必须保证顺序的需求，我们强烈建议不要使用此特性

下面是多主题订阅在java中的例子：

```java
import java.util.regex.Pattern;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.PulsarClient;

PulsarClient pulsarClient = // Instantiate Pulsar client object

// Subscribe to all topics in a namespace

Pattern allTopicsInNamespace = Pattern.compile("persistent://public/default/.*");

Consumer allTopicsConsumer = pulsarClient.subscribe(allTopicsInNamespace, "subscription-1");

// Subscribe to a subsets of topics in a namespace, based on regex

Pattern someTopicsInNamespace = Pattern.compile("persistent://public/default/foo.*");

Consumer someTopicsConsumer = pulsarClient.subscribe(someTopicsInNamespace, "subscription-1");
```

代码例子，请见：

- [Java](http://pulsar.apache.org/docs/en/client-libraries-java#multi-topic-subscriptions)

### 8、主题分区

通常一个topic仅能被一个broker所服务，这限制了topic最大吞吐量。分区topic是特殊的topic类型，他可以被多个broker处理，这让topic有更高的吞吐量。

其实在背后，分区的topic通过N个内部topic实现，N是分区的数量。当向分区的topic发送消息，每条消息被路由到其中一个broker。Pulsar自动处理跨broker的分区分布。

此处，Topic1有5个分区（P0到P4），分布在三个broker上。因为分区多于broker数量，其中有两个broker要处理两个分区。第三个broker则只处理一个。（再次强调，分区的分布是自动处理的）

这个topic的消息被广播给两个consumer。路由模式决定哪个broker处理哪个partition， 订阅模式决定哪条消息送到哪个consumer。

在大多数境况下，路由和订阅模式可以分开指定。通常来讲，吞吐能力的要求，决定了分区/路由的方式。订阅模式则应该由应用的需求来做决定。

分区topic和普通topic，对于订阅模式如何工作，没有任何不同。分区只是决定了从生产者生产消息到消费者处理及确认消息过程中发生的事情。

分区topic需要通过admin API指定创建。创建的时候可以指明分区的数量。

![img](https://img-blog.csdn.net/20181008112143899?watermark/2/text/aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2xpeWltaW5nMjAxNw==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70)

### 9、路由模式

当发布消息到分区topic，你必须要指定路由模式。路由模式决定了每条消息被发布到的分区（其实是内部主题）。

下面是三种默认可用的路由模式

| 模式                     | 描述                                                         | 顺序保证           |
| ------------------------ | ------------------------------------------------------------ | ------------------ |
| Key hash                 | 如果message指定了key，producer将会把key hash，然后把他分配给指定分区 | 同一个key下有序    |
| single default partition | 如果没有key，每个生产者的消息将会被路由分发给专用的分区。初始时候随机选择 | 同一个生产者下有序 |
| round robin分发          | 如果没有key，所有的消息通过round-robin方式被路由到不同的分区，以达到最大的生产能力 | 无                 |

这些默认的模式之外，你还可以创建客制化的路由模式，如果你在使用Java client，可以通过实现MessageRouter接口来做到。

### 10、非持久topic

默认的，Pulsar保存所有没有确认的消息到多个BookKeeper的bookies中（存储节点）。持久topic的消息数据可以在broker重启或者订阅者出问题的情况下存活下来。

Pulsar也提供了非持久topic。非持久topic的消息不会被保存在硬盘上，只存活于内存中。当使用非持久topic分发时，杀掉Pulsar的broker或者关闭订阅者，意味着客户端可能会遭遇消息丢失。

非持久topic有如下格式的名称（注意名字中的non-persistent）：

```
non-persistent://tenant/namespace/topic
```

使用非持久topic的更多信息，请参考 [Non-persistent messaging cookbook](http://pulsar.apache.org/docs/en/cookbooks-non-persistent).

非持久topic中，broker会立即发布消息给所有连接的订阅者，而不会在BookKeeper中存储。如果有一个订阅者断开连接，broker将无法重发这些瞬时消息。订阅者将永远也不能收到这些消息了。去掉持久化存储的步骤，在某些情况下，使得非持久topic的消息比持久topic稍微变快。但是同时，Pulsar的一些核心优势也丧失掉了。

> 非持久topic，消息数据仅存活在内存。如果broker挂掉或者其他情况不能从内存取出，你的消息数据就可能会丢失。只有真的觉得你的使用场景符合，并且你可以忍受时，才可去使用非持久topic。

默认非持久topic在broker上是开启的，你可以通过broker的配置关闭。你可以通过使用pulsar-admin-topics接口管理非持久topic。

#### 10.1、性能

非持久消息通常比持久消息会更快，因为broker无须持久化消息，当消息被分发给所有订阅者时，会立即发送ack给producer。非持久topic让producer有更低的发布延迟。

#### 10.2、Client API

producer和consumer以连接持久topic同样的方式连接到非持久topic。重要的区别是topic的名称必须以non-persistent开头。三种订阅模式--exclusive，shared，failover对于非持久topic都是支持的。

下面是一个非持久topic的java consumer例子：

```java
PulsarClient client = PulsarClient.create("pulsar://localhost:6650");

String npTopic = "non-persistent://public/default/my-topic";

String subscriptionName = "my-subscription-name";

Consumer consumer = client.subscribe(npTopic, subscriptionName);
```

这里还有一个非持久topic的java producer例子： 

```java
Producer producer = client.createProducer(npTopic);
```

### 11、消息存留和过期

#### 11.1、概念

Pulsar broker默认如下：

- 立即删除所有已经被cunsumer确认过的的消息
- 以消息backlog的形式，持久保存所有的未被确认消息

Pulsar有两个特性，这使得你可以覆盖上面的默认行为。

- 消息存留让你可以保存consumer确认过的消息。
- 消息过期让你可以给未被确认的消息设置存活时长（TTL）。（通过自动ACK）

所有消息存留和过期在namespace层面管理。具体操作请查看[Message retention and expiry](http://pulsar.apache.org/docs/en/cookbooks-retention-expiry) 

下图说明了这两种概念：

![img](https://img-blog.csdn.net/20181008112559290?watermark/2/text/aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2xpeWltaW5nMjAxNw==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70)

图中上面的是消息存留，存留规则会被用于某namespace下所有的topic，指明一些消息会被持久存储，即使已经被确认过。没有被留存规则覆盖的消息将会被删除。没有留存规则的话，所有被确认的消息都会被删除。

图中下面的是消息过期，有些消息还没有被确认，也被删除掉了。因为根据设置在namespace上的TTL，他们已经过期了。（例如，TTL为5分钟，过了十分钟消息还没被确认）

#### 11.2、保留策略

- 默认情况下，对于每个主题，broker 会至少保留一个 backlog 消息。 backlog 是特定订阅的未确认的消息的集合。 每个主题可以有多个订阅者，所以每个主题可以有多个 backlog，但是对于没有任何消费者绑定的主题上，不会保留任何消息（默认情况）。
- 当Pulsar消息到达broker时，将一直存储该消息，直到在所有订阅上都确认该消息为止，此时将其标记为删除。通过为给定名称空间中的所有主题设置保留策略，您可以覆盖此行为并保留所有订阅已确认的消息。保留策略是大小限制或时间限制。
- 如果你只打算使用 Reader 接口，消息保留策略会特别有用。 因为 Reader 接口不使用消息确认机制，消息将永远不会存在 backlog 中。 所以大多数实际的 Reader-only 案例都需要配置消息保留。
- 如果设置根据消息大小保留，比如保留10GB，那么命名空间内每个主题的消息，包括已经确认的消息，都会被保留到达到主题的大小限制为止。如果设置根据时间保留，比如保留一天，那么命名空间内每个主题的消息，包括已经确认的消息，都会被保留24小时。 消息保留策略会对没有订阅的主题的所有消息生效，或者对有订阅的主题的已经被确认的消息生效。 消息保留策略不影响订阅主题上的未确认消息 -- 这些消息由 backlog 配额控制。
- 当超过保留限额时，最老的消息会被标记删除，直到保留的消息满足指定的限制范围。

#### 11.3、设置保留策略

##### 11.3.1、默认配置

在 [`broker.conf` ](http://pulsar.apache.org/docs/zh-CN/reference-configuration#broker)配置文件中，默认配置如下，就是不做保留。

`defaultRetentionTimeInMinutes=0`

`defaultRetentionSizeInMB=0`

##### 11.3.2、pulsar-admin

使用[`set-retention`](http://pulsar.apache.org/docs/zh-CN/pulsar-admin#namespaces-set-retention)子命令并指定命名空间，使用`-s`/`--size`参数指定大小限制，使用`-t`/`--time`参数指定时间限制。

示例

为 `my-tenant/my-ns` 命名空间设置10的大小限制和3小时的时间限制：

```shell
$ pulsar-admin namespaces set-retention my-tenant/my-ns \
  --size 10G \
  --time 3h
```

设置保留大小限制，但没有时间限制：

```shell
$ pulsar-admin namespaces set-retention my-tenant/my-ns \
  --size 1T \
  --time -1
```

设置保留无限大小和时间：

```shell
$ pulsar-admin namespaces set-retention my-tenant/my-ns \
  --size -1 \
  --time -1
```

##### 11.3.3、REST API

[**POST** */admin/v2/namespaces/:tenant/:namespace/retention*](https://pulsar.incubator.apache.org/en/admin-rest-api#operation/setRetention)

##### 11.3.4、Java

```java
int retentionTime = 10; // 10 minutes
int retentionSize = 500; // 500 megabytes
RetentionPolicies policies = new RetentionPolicies(retentionTime, retentionSize);
admin.namespaces().setRetention(namespace, policies);
```

#### 11.4、获取保留策略

##### 11.4.1、pulsar-admin

使用 [`get-retention`](http://pulsar.apache.org/docs/zh-CN/pulsar-admin#namespaces)子命令并指定命名空间。

示例

```shell
$ pulsar-admin namespaces get-retention my-tenant/my-ns
{
  "retentionTimeInMinutes": 10,
  "retentionSizeInMB": 0
}
```

##### 11.4.2、REST API

[**GET** */admin/v2/namespaces/:tenant/:namespace/retention*](https://pulsar.incubator.apache.org/en/admin-rest-api#operation/getRetention)

##### 11.4.3、Java

```java
admin.namespaces().getRetention(namespace);
```

#### 11.5、积压配额

##### 11.5.1、概念

用于控制存储未被ACK或者未被处理消息的保留策略

积压是指由Bookies存储的，未确认的主题消息集。 Pulsar将所有未确认的消息存储在积压中，直到它们得到处理和确认为止。

您可以使用积压配额在名称空间级别控制积压的允许大小。设置积压配额涉及设置：

| 策略                        | 触发的操作                                                   |
| :-------------------------- | :----------------------------------------------------------- |
| `producer_request_hold`     | Broker 会持有生产者投递的消息，但并不会把投递的消息进行持久化存储 |
| `producer_exception`        | Broker 会与客户端断开连接并抛出异常                          |
| `consumer_backlog_eviction` | Broker 将开始丢弃backlog的消息                               |

> 注意保留策略类型之间的区别:                                                                                                                                     

> 你可能已经注意到，Pulsar 中关于“保留策略”有两种定义，一种适用于已确认消息的持久存储，一种适用于 backlog。

##### 11.5.2、设置大小阈值和 backlog 保留策略

通过指定[命名空间](http://pulsar.apache.org/docs/zh-CN/reference-terminology#namespace)的大小限制和策略，为命名空间中的所有主题设置大小阈值和 backlog 保留策略。

- pulsar-admin

使用[`set-backlog-quota`](http://pulsar.apache.org/docs/zh-CN/pulsar-admin#namespaces)子命令，并使用 `-l`/`--limit` 参数指定命名空间`大小限制 ，以及使用 <code>-p`/`--policy` 参数指定保留策略。

示例

```shell
$ pulsar-admin namespaces set-backlog-quota my-tenant/my-ns \
  --limit 2G \
  --policy producer_request_hold
```

- REST API

[**POST** */admin/v2/namespaces/:tenant/:namespace/backlogQuota*](https://pulsar.incubator.apache.org/en/admin-rest-api#operation/getBacklogQuotaMap)

- Java

```java
long sizeLimit = 2147483648L;
BacklogQuota.RetentionPolicy policy = BacklogQuota.RetentionPolicy.producer_request_hold;
BacklogQuota quota = new BacklogQuota(sizeLimit, policy);
admin.namespaces().setBacklogQuota(namespace, quota);
```

#### 11.6、获取 backlog 大小阈值和 backlog 保留策略

可以查看已对命名空间应用的大小阈值和 backlog 保留策略。

##### 11.6.1、pulsar-admin

Use the [`get-backlog-quotas`](http://pulsar.apache.org/docs/zh-CN/pulsar-admin#pulsar-admin-namespaces-get-backlog-quotas) subcommand and specify a namespace. 下面是一个示例：

```shell
$ pulsar-admin namespaces get-backlog-quotas my-tenant/my-ns
{
  "destination_storage": {
    "limit" : 2147483648,
    "policy" : "producer_request_hold"
  }
}
```

##### 11.6.2、REST API

[**GET** */admin/v2/namespaces/:tenant/:namespace/backlogQuotaMap*](https://pulsar.incubator.apache.org/en/admin-rest-api#operation/getBacklogQuotaMap)

##### 11.6.3、Java

```java
Map<BacklogQuota.BacklogQuotaType,BacklogQuota> quotas =
  admin.namespaces().getBacklogQuotas(namespace);
```

#### 11.7、移除 backlog quotas

##### 11.7.1、pulsar-admin

Use the [`remove-backlog-quota`](http://pulsar.apache.org/docs/zh-CN/pulsar-admin#pulsar-admin-namespaces-remove-backlog-quota) subcommand and specify a namespace. 下面是一个示例：

```shell
$ pulsar-admin namespaces remove-backlog-quota my-tenant/my-ns
```

##### 11.7.2、REST API

[**DELETE** */admin/v2/namespaces/:tenant/:namespace/backlogQuota*](https://pulsar.incubator.apache.org/en/admin-rest-api#operation/removeBacklogQuota)

##### 11.7.3、Java

```java
admin.namespaces().removeBacklogQuota(namespace);
```

#### 11.8、清除 backlog

##### 11.8.1、pulsar-admin

使用 [`clear-backlog`](http://pulsar.apache.org/docs/zh-CN/pulsar-admin#pulsar-admin-namespaces-clear-backlog) 子命令。

```shell
$ pulsar-admin namespaces clear-backlog my-tenant/my-ns
```

默认情况下，系统将提示您确保确实要清除名称空间的待办事项。您可以使用`-f` /`--force`标志覆盖提示。

#### 11.9、生存时间（TTL)

默认情况下，Pulsar 会永久存储所有未确认的消息。 在大量消息未得到确认的情况下，可能会导致大量磁盘空间的使用。 如果需要考虑磁盘空间，可以设置生存时间(TTL)，以确定未确认的消息将保留多长时间。

##### 11.9.1、为名称空间设置生存时间（TTL）

- 11.9.1.1、pulsar-admin

使用[ `set-message-ttl` ](http://pulsar.apache.org/docs/zh-CN/pulsar-admin#pulsar-admin-namespaces-set-message-ttl)子命令并指定命名空间和TTL(以秒为单位，使用`-ttl`/`--messageTTL`参数指定)。

示例

```shell
$ pulsar-admin namespaces set-message-ttl my-tenant/my-ns \
  --messageTTL 120 # TTL of 2 minutes
```

- REST API

[**POST** */admin/v2/namespaces/:tenant/:namespace/messageTTL*](https://pulsar.incubator.apache.org/en/admin-rest-api#operation/setNamespaceMessageTTL)

- Java

```java
admin.namespaces().setNamespaceMessageTTL(namespace, ttlInSeconds);
```

##### 11.9.2、获取命名空间的生存时间(TTL) 配置

- pulsar-admin

使用 [`get-message-ttl`](http://pulsar.apache.org/docs/zh-CN/pulsar-admin#pulsar-admin-namespaces-get-message-ttl) 子命令并指定命名空间。

示例

```shell
pulsar-admin namespaces get-message-ttl my-tenant/my-ns
60
```

- REST API

[**GET** */admin/v2/namespaces/:tenant/:namespace/messageTTL*](https://pulsar.incubator.apache.org/en/admin-rest-api#operation/getNamespaceMessageTTL)

- Java

```java
admin.namespaces().getNamespaceMessageTTL(namespace)
```

### 12、消息去重

当消息被Pulsar持久化多于一次的时候，会发生数据重复。消息去重是Pulsar可选的特性，阻止不必要的消息重复，每条消息仅处理一次。

下图展示了开启和关闭消息去重的场景：

![img](https://img-blog.csdn.net/20181008112645547?watermark/2/text/aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2xpeWltaW5nMjAxNw==/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70)

最上面的场景中，消息去重被关闭。producer发布消息1到一个topic，消息到达broker后，被持久化到BookKeeper。然后producer又发送了消息1（可能因为某些重试逻辑），然后消息被接收后又持久化在BookKeeper。消息重复发生了。

在第二个场景中，producer发送了消息1，消息被broker接收然后持久化，和第一个场景是一样的。当producer再次发送消息时，broker知道已经收到个消息1，所以不会再持久化消息1.

> 消息去重在命名空间层面处理。更多介绍请参考[message deduplication cookbook](http://pulsar.apache.org/docs/en/cookbooks-deduplication).
>
> Pulsar中启用消息去重，你必须同时配置 Pulsar broker 和客户端。

#### 12.1、启用方式

默认是都禁用的

> 在broker层对所有命名空间启用/禁用。
>
> 指定命名空间启用/禁用。

#### 12.2、去重配置

broker.conf配置文件修改

| Parameter                                             | Description                                                  | Default         |
| :---------------------------------------------------- | :----------------------------------------------------------- | :-------------- |
| `brokerDeduplicationEnabled`                          | broker中的默认去重配置，如果设置为true, 那么默认所有命名空间都启用; 否则，必须在每个命名空间上进行启用/禁用。 | `false`         |
| `brokerDeduplicationMaxNumberOfProducers`             | 为重复数据删除而存储信息的最大生产者数量。                   | `10000`         |
| `brokerDeduplicationEntriesInterval`                  | 去重快照生成后，允许存储的去重信息数量。较大的时间间隔会导致拍摄更少的快照，尽管这会延长主题恢复时间（快照重放后发布的条目所需的时间）。 | `1000`          |
| `brokerDeduplicationProducerInactivityTimeoutMinutes` | 空闲时间（以分钟为单位）之后代理将丢弃与断开生产者相关的重复数据删除信息。 | `360` (6 hours) |

命令行修改

```shell
# 启用
bin/pulsar-admin namespaces set-deduplication public/default --enable # or just -e
# 禁用
bin/pulsar-admin namespaces set-deduplication public/default --disable # or just -d
```

如果在Pulsar代理中启用消息重复数据删除，则需要为客户端生产者完成以下任务：

- 为生产者指定名称。
- 将消息超时设置为0（即无超时）。

```java
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import java.util.concurrent.TimeUnit;

PulsarClient pulsarClient = PulsarClient.builder()
        .serviceUrl("pulsar://localhost:6650")
        .build();
Producer producer = pulsarClient.newProducer()
        .producerName("producer-1")
        .topic("persistent://public/default/topic-1")
        .sendTimeout(0, TimeUnit.SECONDS)
        .create();
```

## 四、基于地理位置复制

**异地复制**是跨 Pulsar 实例的多个集群复制持久存储的消息数据，Pulsar 支持在不同的地理位置生产和消费消息。 

例如，应用程序在某一地区或市场中发布数据，但需要在其他地区或市场处理和消费数据。 Pulsar 的[跨机房复制](http://pulsar.apache.org/docs/zh-CN/administration-geo)可以实现这一操作。

下图说明了 Pulsar 在不同集群之间跨地域复制的过程：

![Replication Diagram](http://pulsar.apache.org/docs/assets/geo-replication.png)

在此图中，**每当 P1、P2**和 **P3**生产者分别向**集群A**、集群-B**和**集群-C**，集群上的 T1 主题发布消息时，**这些消息都会在集群之间立即复制。 复制消息后**，C1**和**C2**使用者可以使用来自其各自集群的消息，如果没有异地复制**，C1** **和 C2**使用者将无法使用**P3 生产者**发布的消息。

### 1、跨地域复制和 Pulsar 属性

必须在Pulsar中按租户启用地理复制。仅当创建允许访问两个集群的租户时，才能在集群之间启用地理复制。

尽管必须在两个集群间启用地理复制，但跨域复制是在命名空间级别管理的。 要启动名称空间级别的跨域复制，你必须完成以下操作。

- 激活geo-replication名称空间
- 配置该名称空间使其可以跨两个或多个集群复制

在指定名称空间中，任何主题上发布的任何消息都将复制到指定集中的所有集群。

### 2、本地存储和转发

您必须在 Pulsar 中启用基于每个租户的异地复制。只有在创建允许访问两个集群的租户时，才能在集群之间启用异地复制。

尽管必须在两个集群之间启用异地复制，但实际上异地复制在命名空间级别进行管理。您必须完成以下任务才能为命名空间启用异地复制：

- 启用异地复制命名空间。
- 配置该命名空间以跨两个或多个预配集群进行复制。

在该命名空间中的任何主题上发布的任何消息都复制到指定集的所有集群。

### 3、本地持久性和转发

在 Pulsar 主题上生成消息时，消息首先保留在本地集群中，然后异步转发到远程集群。

在正常情况下，当连接问题为无时，将立即复制消息，同时向本地使用者发送消息。通常，远程区域之间的网络往返时间 （RTT：round-trip time） 定义端到端交付延迟。

应用程序可以在任何集群中创建生产者和使用者，即使远程集群不可访问（如在网络分区期间）。

生产者和消费者可以向 Pulsar 实例中的任何集群发布消息和消费消息。然而，订阅不仅能订阅创建了订阅的集群，还可以在启用复制订阅后在集群之间传输订阅。启用复制订阅后，可以保持订阅状态同步。因此，主题可以异步地跨多个地理区域复制。在故障转移的情况下，使用者可以重新启动消费来自其他集群中的故障点的消息。

在上述示例中**，T1 主题**在三个集群（**集群-A**、**集群-B**和**集群-C**）之间复制。

在三个集群中的任何一个中生成的所有消息都传递到其他集群中的所有订阅。在这种情况下**，C1 和** **C2**使用者会接收**P1、P2**和**P3 生产者发布**的所有消息。 在每个生产者基础上仍然保证顺序性。

### 4、配置复制

如异地复制和 Pulsar 属性部分所述，Pulsar 中的异地复制在租户级别进行管理。

#### 1、向属性授予权限

要复制到集群，租户需要使用该集群的权限。您可以在创建租户时向租户授予权限或稍后授予权限。

创建租户时指定所有预期集群：

```shell
$ bin/pulsar-admin tenants create my-tenant \
  --admin-roles my-admin-role \
  --allowed-clusters us-west,us-east,us-cent
```

若要更新现有租户的权限，请使用`update`而不是`create`。

#### 2、启用异地复制命名空间

您可以使用以下命令示例创建命名空间。

```shell
$ bin/pulsar-admin namespaces create my-tenant/my-namespace
```

最初，命名空间不分配给任何集群。您可以使用子命令将命名空间分配给集群：`set-clusters`

```shell
$ bin/pulsar-admin namespaces set-clusters my-tenant/my-namespace \
  --clusters us-west,us-east,us-cent
```

您可随时更改命名空间的复制集群，而不会中断正在进行的流量。配置更改后，复制通道将立即在所有集群中设置或停止。

#### 3、使用具有异地复制的主题

创建异地复制命名空间后，生产者或使用者在该命名空间中创建的任何主题将跨集群复制。通常，每个应用程序都将serviceUrl用于本地集群。

#### 4、选择性复制

默认情况下，消息将复制到为命名空间配置的所有集群。您可以通过为邮件指定复制列表来选择性地限制复制，然后仅将该消息复制到复制列表中的子集。

以下是 Java API 的示例。请注意在构造 Message 对象时使用的方法：`setReplicationClusters`

```java
List<String> restrictReplicationTo = Arrays.asList(
        "us-west",
        "us-east"
);

Producer producer = client.newProducer()
        .topic("some-topic")
        .create();

producer.newMessage()
        .value("my-payload".getBytes())
        .setReplicationClusters(restrictReplicationTo)
        .send();
```

#### 5、主题统计信息

可通过脉冲星管理工具和 REST API 获得`特定于主题的异地`复制主题的统计信息：

```shell
$ bin/pulsar-admin persistent stats persistent://my-tenant/my-namespace/my-topic
```

每个集群报告自己的本地统计信息，包括传入和传出复制速率和积压工作。

#### 6、删除异地复制主题

鉴于异地复制主题存在于多个区域中，因此无法直接删除异地复制主题。相反，您应该依赖于自动主题垃圾回收。

在 Pulsar 中，当主题满足以下三个条件时，将自动删除主题：

- 没有生产者或消费者与它有联系;
- 没有订阅;
- 不再留有任何信息以进行保留。对于异地复制主题，每个区域使用容错机制来决定在本地删除主题是否安全。

您可以通过在代理配置中设置brokerDeleteInactiveTopicsEnabled为false来显式禁用主题垃圾回收。

若要删除异地复制主题，请关闭该主题上的所有生产者和使用者，并删除每个复制集群中的所有本地订阅。当 Pulsar 确定整个系统没有保留该主题的有效订阅时，它将垃圾收集主题。

### 5、复制的订阅

Pulsar 支持复制的订阅，因此，在不到一秒的时间内，您可以在跨多个地理区域异步复制的主题上下文中保持订阅状态同步。

在故障转移的情况下，使用者可以从其他集群中的故障点重新启动使用。

#### 1、启用复制的订阅

默认情况下，复制的订阅处于禁用状态。您可以在创建使用者时启用复制的订阅。

```java
Consumer<String> consumer = client.newConsumer(Schema.STRING)
            .topic("my-topic")
            .subscriptionName("my-subscription")
            .replicateSubscriptionState(true)
            .subscribe();
复制
```

#### 2、优势

- 实现逻辑很容易。
- 您可以选择启用或禁用复制的订阅。
- 启用它时，开销较低，并且易于配置。
- 禁用它时，开销为零。

#### 3、限制

启用复制订阅时，将创建一致的分布式快照，以在不同集群的消息 ID 之间建立关联。快照是定期拍摄的。默认值为1s 。这意味着，使用者故障到其他集群可能会收到 1 秒的重复项。您还可以在broker.conf文件中配置快照的频率。

## 五、Pulsar 客户端

Pulsar向用户暴露原生的[Java](https://crowdin.com/backend/phrases/client-libraries-java.md)和[C++](https://crowdin.com/backend/phrases/client-libraries-cpp.md)的客户端API。

Puslar客户端封装并优化了客户端-服务器的通信协议，并暴露出一套简单直观的API，提供给应用程序使用。

底层实现上，目前官方版的Pulsar客户端支持对用户透明的连接重连、故障切换、未ack消息的缓冲、消息重传。

> ### 自定义客户端库
>
> 如果您想创建自己的客户端库, 我们建议参考Pulsar的自定义 [二进制协议](https://crowdin.com/backend/phrases/developing-binary-protocol.md) 的文档。

##  

### 1、客户端使用步骤

当应用程序要创建生产者/消费者时, Pulsar客户端库执行按以下两个步骤的工作:

1. 客户端将尝试通过向服务器（Broker）发送 HTTP 查找请求，来确定主题（Topic）所在的服务器（Broker）。客户端通过查询Zookeeper中（缓存）的元数据，来确定这条消息的topic在哪个broker上，如果该topic不在任何一个broker上，则把这个topic分配在负载最少的broker上。
2. 当客户端获取了broker的地址之后，将会创建一个TCP连接（或复用连接池中的连接）并且进行鉴权。客户端和broker通过该连接交换基于自定义协议的二进制命令。同时，客户端会向broker发送一条命令用以在broker上创建生产者/消费者，该命令将会在验证授权策略后生效。

每当 TCP 连接中断时, 客户端将立即重新启动此安装阶段, 并将继续尝试使用指数退避重新建立生产者或使用者, 直到操作成功为止。

### 2、Reader 接口

在Pulsar中, "标准" [消费者接口](https://crowdin.com/backend/phrases/concepts-messaging.md#consumers) 涉及使用消费者监听 [主题](https://crowdin.com/backend/phrases/reference-terminology.md#topic), 处理传入消息, 并在处理完这些消息后最终确认它们。 每当使用者连接到某个主题时, 它就会自动开始从最早的没被确认(unacked)的消息处读取, 因为该主题的游标是由Pulsar自动管理的。

使用Pulsar的 **读取器接口**, 应用程序可以手动管理游标。 当使用读取器连接到一个主题而非消费者时，在读取器连接到主题的时候就需要指定读取器从哪个位置开始读消息。

当连接到某个主题时, 读取器从以下位置开始读消息:

- 主题中的 **最早的** 可用消息
- 主题中的 **最新** 可用消息
- 除最早的和最新的之外的可用消息位点。如果你使用该选项，你需要显式的提供一个消息ID。你的应用程序需要自己管理消息ID，例如从持久化的数据存储或缓存中获取

Pulsar的读取器接口在流计算场景下，对提供[effective-once](https://streaml.io/blog/exactly-once/)的语义很有帮助。 Pulsar能够将主题的消息进行重放，并从重放后的位置开始读取消息，是满足流处理的场景的重要基础。读取器界面为Pulsar客户端在主体内提供了一种底层抽象“手动管理的位点”

![img](https://img-blog.csdnimg.cn/20181105141419905.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2xpeWltaW5nMjAxNw==,size_16,color_FFFFFF,t_70)

> ### 未分区主题
>
> Pulsar的读取器目前无法在[已分区的主题](https://crowdin.com/backend/phrases/concepts-messaging.md#partitioned-topics)上使用。

下面是一个Java语言实现的从主题上最早可用消息的位置开始消费的例子：

```java
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Reader;
// Create a reader on a topic and for a specific message (and onward)
Reader<byte[]> reader = pulsarClient.newReader()
    .topic("reader-api-test")
    .startMessageId(MessageId.earliest) 
    .create();

while (true) {
    Message message = reader.readNext();
    // Process the message
}
```

创建一个从最新可用消息处开始读取消息的读取器 

```java
Reader<byte[]> reader = pulsarClient.newReader()
    .topic(topic)
    .startMessageId(MessageId.latest)
    .create();
```

创建一个从其他位置（非最早可用且非最新可用消息处）读取消息的读取器

```
byte[] msgIdBytes = // Some byte array
MessageId id = MessageId.fromByteArray(msgIdBytes);
Reader<byte[]> reader = pulsarClient.newReader()
    .topic(topic)
    .startMessageId(id)
    .create();
```

## 六、多租户和命名空间

Pulsar从底层就是作为多租户系统来创建的。 为了支持多租户，Pulsar有着租户的概念， 租户可以被跨集群传播，每个租户都可以使用他们自己的认证和鉴权scheme。 租户也是存储配额的管理单元，, message TTL，同时也是可被管理的规则隔离。

Pulsar多租户的天性，在topic的URL映射上充分显现，结构如下：

```
persistent://tenant/namespace/topic
```

正如您所看到的, 租户是topic的最基本的分类单元 (比namespace和topic名称更基本)。

### 1、租户

对于Pulsar实例中的每个租户，你可以分配给他：

- 授权scheme
- 租户配置适用的cluster集合

### 2、Namespaces

租户和namespace是Pulsar支持多租户的两个关键概念。

- Pulsar为指定租户配置了适当的容量分配。
- Namespace是租户中管理单元的命名。 命名空间上设置的配置策略，适用于该命名空间中创建的所有主题。 租户可以通过自我管理，使用 REST API 和 pulsar-admin CLI 工具, 创建多个命名空间。例如，一个对接多个应用的租户，可以为每个应用创建不同的namespace。

同一命名空间中主题的名称如下所示:

```
persistent://tenant/app1/topic-1

persistent://tenant/app1/topic-2

persistent://tenant/app1/topic-3
```

## 七、认证授权

Pulsar支持可插入的认证机制，它配置在broker上，并且支持授权，来识别客户端及它能够访问的topic和tenant。

```http
http://pulsar.apache.org/docs/en/concepts-authentication/
```

## 八、Topic压缩

消息数据高度可扩展的持久存储是Pulsar构建的主要目标。 Pulsar的topic让你可以持久存储你所需要的这么多未被确认的消息，同时保留了消息的顺序。 默认的，Pulsar存储生产到主题上所有未被确认/未被处理的消息。 在很多Pulsar的使用案例中，在topic累计大量的未被确认的消息是有必要的。但对于Pulser的consumer来说，在完整的消息log中进行回退，将变得非常耗时。

更多topic压缩实践的指南，请参考[Topic compaction cookbook](http://pulsar.apache.org/docs/en/cookbooks-compaction/)

某些情况下，consumer并不需要完整的topic日志。 他们可能只需要几个值来构造一个更 "浅" 的日志图像, 也许仅仅只是最近的值。 对于这种应用场景，Pulsar提供了 topic压缩. 当你在topic上执行压缩，Pulsar会遍历topic的backlog然后把遥远模糊已经有了更新的消息移除。例如，它遍历一个以key为基础的topic，只留下关联到key上最新的消息。

### 1、Pulsar的topic压缩特性：

- 允许通过主题日志更快地 "后退"
- 仅适用于 持久性topic
- 当backlog达到一定大小时，可以被自动出发，或者通过命令行手动出发。请参见Topic compaction cookbook
- 在概念上和操作上与 retention和expiry 是不同的。 但是，在topic压缩中，还是会尊重retention。 如果retention已经从topic的backlog中移除了消息，那么此条消息在压缩的topic账簿上也是无法被读取的。

### 2、Topic压缩示例：股票报价机

Pulsar topic压缩的一个使用例子，可以看一下股票报价机topic。 在股票报价机topic中，每条消息都有带有时间戳的股票买入价格（包含代表股票符号的消息key，例如AAPL或者GOOG）。 可能你感兴趣的只是股票报价机中最新的股票价格，而对历史数据并不感兴趣（即你不需要构建topic每个key下消息序列的完整图像）。 压缩在这种场景下非常的方便，因为它使得用户不需回退到模糊的消息上。

### 3、Topic压缩的工作原理

当[通过命令行](http://pulsar.apache.org/docs/en/cookbooks-compaction/)触发topic压缩，Pulsar将会从头到尾迭代整个topic。 对于它碰到的每个key，压缩程序将会只保留这个key最近的事件。

之后，broker将会创建一个新的[BookKeeper ledger ](http://pulsar.apache.org/docs/en/concepts-architecture-overview/#ledgers)然后开始对topic的每条消息进行第二次迭代。 对于每条消息，如果key匹配到它的最新事件，key的数据内容，消息ID，元数据将会被写入最新创建的ledger。 如果key并没有匹配到最新的消息，消息将被跳过。 如果给定的消息，负载是空的，它将被跳过并且视为删除（类似key-value数据库中的 [tombstones](https://en.wikipedia.org/wiki/Tombstone_(data_store))概念）； 在本topic第二次迭代结束时，新创建的BookKeeper ledger将被关闭，并将两个内容写入元数据 ：BookKeeper ledger的ID及最新被压缩的消息的ID（这被称为topic的压缩层位）。 写入元数据后，压缩就完成了。

初始化压缩操作完成后，将来任何对压缩层位及压缩backlog的修改，都会通知给拥有该topic的Pulsar [broker](http://pulsar.apache.org/docs/en/reference-terminology#broker)。

当下列更改发生时：

- 启用读取压缩功能的客户端（consumer和reader），将会尝试从topic中读取消息，或者：
  - 像从正常的主题那样读取（如果消息的ID大于等于压缩层位），或
  - 从压缩层位的开始读取（如果消息ID小于压缩层位）

## 九、分层存储

Pulsar面向segment的架构允许topic backlog增长的十分庞大，如果有没加以限制，随着时间的增加，代价将越来越高。

有一个减轻这个消耗的办法，就是使用分层存储。使用分层存储，backlog中旧的消息，将被从booKeeper转移到更低廉的存储机制中，当然还是允许客户端进入backlog，就像什么也没有发生一样。

> 写入bookKeeper的数据被默认复制到3台物理机。但是，一旦segment在bookKeeper中被封闭，将会变成不可改变，并且可以被拷贝到长期存储中。长期存储可以达到节省存储的目的，通过使用如 [Reed-Solomon error correction](https://en.wikipedia.org/wiki/Reed–Solomon_error_correction) 的机制，只需更少的物理数据备份

Pulsar目前支持S3作为长期存储。 可以通过Rest API或者命令行接口，卸载数据到S3。 用户传入他们想要保留在bookKeeper的topic数据的数量，broker将会拷贝backlog到S3. 原始数据将在配置的延迟时长后，从bookKeeper中删除（默认4小时）。

![img](https://img-blog.csdnimg.cn/201811050916011.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2xpeWltaW5nMjAxNw==,size_16,color_FFFFFF,t_70) 

搭建分层存储的指导，请参考 [Tiered storage cookbook](https://crowdin.com/backend/phrases/cookbooks-tiered-storage.md)。

## 十、Schema注册

Scheme注册是Pulsar保证数据类型安全的机制。

### 1、Schema注册表

对于围绕消息总线（如pulsar）搭建的应用来说，类型安全非常重要。 生产者和消费者需要某种机制来协调topic层面的类型, 以免出现各种各样的潜在问题 (例如序列化和反序列化问题)。 应用通常采用两种基本方法之一，在消息传递中确保类型安全:

1. "客户端" 方法, 消息生产者和消费者不仅负责序列化和反序列处理消息 (包括原始字节), 而且还 "知道" 哪些类型是通过哪些topic传输的。 如果生产者往主题 topic-1发送温度感应数据，而这个topic的consumer按照读取湿度感应的方式来转换数据，将会遇到问题。
2. "服务器端" 方法, 生产者和消费者告知系统哪些数据类型可以通过topic传输。 通过这种方法，消息系统强制执行类型安全, 并确保生产者和消费者保持同步。

这两种方法都被Pulsar支持，你可以在topic的基础上，自由选择采用哪一种，或者混用。

1. "客户端" 方法中, 生产者和消费者可以发送和接收由原始字节数组组成的消息, 并在应用中保留所有的类型安全，以“带外”基础上强制执行。
2. "服务器端" 方法中，Pulsar有一个内置的 schema registry，它使客户端能够根据每个topic载入数据schema。 这些schema显示了，topic可以识别哪些数据类型为有效。

Pulsar的schema注册当前仅能在 Java 客户端使用。

### 2、基础架构

当你使用Schema创建带类型的producer时，schema将会被自动上传。 此外，可以通过Pulsar的REST API 手动上载、提取和更新schema。

其他schema注册的后端

开箱即用，Pulsar使用 Apache BookKeeper log存储系统来保存schema。 不过你也可以按照你的需要使用不同的后端。 客制化的schema存储逻辑文档即将推出。

### 3、Schema是如何工作的

Pulsar schema应用于主题层面（schema无法应用于namespace和tenant层面），并且被强制执行，producer和consumer上传schema到Pulsar broker。

Pulsar schema是非常简单的数据结构，它的构成如下：

- name。Pulsar中, schema的名称是schema应用于的topic。
- payload , 它是schema的二进制表示形式。
- Schema类型
- 用户定义的字符串/字符串映射map, properties 。 Properties如何使用，完全由程序指定。 Properties可能是关联到schema的Git hash，例如dev或者prod等环境信息。

### 4、Schema版本

我们通过一个例子，来讲解schema版本如何工作。 假设使用以下代码创建Pulsar Java 客户端，尝试连接到Pulsar，并开始发送消息:

```java
PulsarClient client = PulsarClient.builder()
        .serviceUrl("pulsar://localhost:6650")
        .build();

Producer<SensorReading> producer = client.newProducer(JSONSchema.of(SensorReading.class))
        .topic("sensor-data")
        .sendTimeout(3, TimeUnit.SECONDS)
        .create();
```

下表列出了尝试连接时可能出现的场景, 以及每种场景下发生了什么：

| **场景**                                                 | **发生了什么**                                               |
| -------------------------------------------------------- | ------------------------------------------------------------ |
| 该Topic不存在schema                                      | 使用给定的schema创建了producer。 Schema传输到broker并被存储 (因为没有现成的schema与SensorReading schema "兼容")。 任何使用同样schema/topic的consumer可以消费sensor-datatopic中的消息。 |
| Schema已经存在；producer使用已经被保存过的schema进行连接 | Schema被传输到Pulsar broker。 Broker确认此schema是兼容的。 Broker尝试在BookKeeper存储schema，但是发现它已经存在了，所以用它来标记生产的消息。 |
| Schema已经存在；producer使用兼容的新schema进行连接。     | Producer传输schema到broker。broker发现这个schema是兼容的，随后保存这个新schema作为当前版本（使用新的版本号）。 |

Schema版本是连续的。 Schema被处理关联topic的broker所保存，便于进行版本的分配。 一旦分配/提供了schema的版本，后续由该生产者生产的所有消息，都将被恰当的版本所标记。

### 5、支持的schema格式

以下格式被Pulsar schema注册所支持：

无。如果没有为topic指定schema，producer和consumer将会处理原始字节。

- String (用于 UTF-8-encoded 字符串)
- JSON
- Protobuf
- Avro

### 6、管理Schema

你可以使用Pulsar的admin tools来管理topic的schema