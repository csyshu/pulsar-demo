## Pulsar

### 一、单机版本地安装

对于本地开发和测试，可以在计算机上以独立模式运行Pulsar。独立模式包括在单个Java虚拟机（JVM）进程中运行的Pulsar代理，必要的ZooKeeper和BookKeeper组件。

生产环境安装参考： [Deploying a Pulsar instance](http://pulsar.apache.org/docs/en/deploy-bare-metal)

#### 1、系统要求

- MacOS、Linux系统
- Java8
- 默认会分配2G内存用于启动，也可以通过修改PULSAR_MEM下面conf/pulsar_env.sh文件中的配置进行变更。

#### 2、下载

- download from the Apache mirror ([Pulsar 2.6.2 binary release](https://archive.apache.org/dist/pulsar/pulsar-2.6.2/apache-pulsar-2.6.2-bin.tar.gz))
- download from the Pulsar [downloads page](http://pulsar.apache.org/download)
- download from the Pulsar [releases page](https://github.com/apache/pulsar/releases/latest)
- use [wget](https://www.gnu.org/software/wget)：$ wget https://archive.apache.org/dist/pulsar/pulsar-2.6.2/apache-pulsar-2.6.2-bin.tar.gz

#### 3、解压

```
tar xvfz apache-pulsar-2.6.2-bin.tar.gz 
cd apache-pulsar-2.6.2
```

#### 4、初始化目录

| Directory  | Contains                                                     |
| :--------- | :----------------------------------------------------------- |
| `bin`      | Pulsar's command-line tools, such as [`pulsar`](http://pulsar.apache.org/docs/en/reference-cli-tools#pulsar) and [`pulsar-admin`](http://pulsar.apache.org/docs/en/pulsar-admin). |
| `conf`     | Configuration files for Pulsar, including [broker configuration](http://pulsar.apache.org/docs/en/reference-configuration#broker), [ZooKeeper configuration](http://pulsar.apache.org/docs/en/reference-configuration#zookeeper), and more. |
| `examples` | A Java JAR file containing [Pulsar Functions](http://pulsar.apache.org/docs/en/functions-overview) example. |
| `lib`      | The [JAR](https://en.wikipedia.org/wiki/JAR_(file_format)) files used by Pulsar. |
| `licenses` | License files, in the`.txt` form, for various components of the Pulsar [codebase](https://github.com/apache/pulsar). |

#### 5、初次运行生成目录

| Directory   | Contains                                                     |
| :---------- | :----------------------------------------------------------- |
| `data`      | The data storage directory used by ZooKeeper and BookKeeper. |
| `instances` | Artifacts created for [Pulsar Functions](http://pulsar.apache.org/docs/en/functions-overview). |
| `logs`      | Logs created by the installation.                            |

#### 6、启动

可以使用pulsar命令启动本地集群，该命令存储在bin目录中，并指定要以独立模式启动Pulsar

```
# 普通启动集群命令
bin/pulsar standalone
# 后台启动命令
pulsar-daemon start standalone
```

启动成功会看到如下信息

```
2017-06-01 14:46:29,192 - INFO  - [main:WebSocketService@95] - Configuration Store cache started
2017-06-01 14:46:29,192 - INFO  - [main:AuthenticationService@61] - Authentication is disabled
2017-06-01 14:46:29,192 - INFO  - [main:WebSocketService@108] - Pulsar WebSocket Service started
```

`注意事项`

- 启动后服务会运行在由你控制的终端设备上，如果你要执行其他命令，需要打开新的命令行窗口，你也可以后台启动服务，通过命令pulsar-daemon start standalone。更多信息，查看 [pulsar-daemon](https://pulsar.apache.org/docs/en/reference-cli-tools/#pulsar-daemon)。
- 默认情况下，没有配置加密，认证和授权。这时可以不经过任何授权进行远程访问。请检查 [Security Overview](http://pulsar.apache.org/docs/en/security-overview) 文档以保护您的部署。
- 当你启动本地集群时，会自动创建`public/default` [namespace](http://pulsar.apache.org/docs/en/concepts-messaging#namespaces)。这个名称空间用于开发目的，所有的topic都在名称空间内进行管理，更多信息，查看 [Topics](http://pulsar.apache.org/docs/en/concepts-messaging#topics)。

#### 7、使用

Pulsar提供了叫做pulsar-client的CTL工具，这个工具可以让你在集群上进行发送和消费消息到对应主题。

##### `Consume`

```
bin/pulsar-client consume my-topic -s "first-subscription"
```

`命令说明：`

```
该命令作用是订阅主题为my-topic，指定名称为first-subscription的消费
```

`消费日志`

```
09:56:55.566 [pulsar-client-io-1-1] INFO  org.apache.pulsar.client.impl.MultiTopicsConsumerImpl - [TopicsConsumerFakeTopicNamee2df9] [first-subscription] Success subscribe new topic my-topic in topics consumer, partitions: 4, allTopicPartitionsNumber: 4
```

`提示：我们没有创建topic，在消费消息前，这是因为，如果我们消费或者发送消息到一个不存在的topic中时，Pulsar会为我们自动创建topic。`

##### `Produce`

```
$ bin/pulsar-client produce my-topic --messages "hello-pulsar"
```

`命令说明：`

```
该命令作用是将消息hello-pulsar发送至my-topic主题下
```

`生产日志：`

```
13:09:39.356 [main] INFO  org.apache.pulsar.client.cli.PulsarClientTool - 1 messages successfully produced
```

#### 8、停止

```
# 非后台启动情况
Ctrl+C
# 后台启动的情况
pulsar-daemon stop standalone
```

### 二、在Docker上运行Pulsar

对于本地开发和测试，可以在本地Docker容器中，以独立模式运行Pulsar。如果尚未安装Docker，请下载 [Community edition](https://www.docker.com/community-edition)并按照您的操作系统的说明进行操作。
