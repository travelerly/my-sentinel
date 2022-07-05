# Sentinel: 分布式系统的流量防卫兵

[官方文档](https://github.com/alibaba/Sentinel/wiki/%E4%BB%8B%E7%BB%8D)

[Awesome Sentinel](./awesome-sentinel.md)

## Sentinel 是什么？

Sentinel 是分布式系统的防御系统。以流量为切入点，通过动态设置的流量控制、服务熔断降级、系统负载保护等多个维度保护服务的稳定性，通过服务降级增强服务被拒后用户的体验。



### Sentinel 具有以下特征:

- **丰富的应用场景**：Sentinel 承接了阿里巴巴近 10 年的双十一大促流量的核心场景，例如秒杀（即突发流量控制在系统容量可以承受的范围）、消息削峰填谷、集群流量控制、实时熔断下游不可用应用等。
- **完备的实时监控**：Sentinel 同时提供实时的监控功能。您可以在控制台中看到接入应用的单台机器秒级数据，甚至 500 台以下规模的集群的汇总运行情况。
- **广泛的开源生态**：Sentinel 提供开箱即用的与其它开源框架/库的整合模块，例如与 Spring Cloud、Apache Dubbo、gRPC、Quarkus 的整合。您只需要引入相应的依赖并进行简单的配置即可快速地接入 Sentinel。同时 Sentinel 提供 Java/Go/C++ 等多语言的原生实现。
- **完善的 SPI 扩展机制**：Sentinel 提供简单易用、完善的 SPI 扩展接口。您可以通过实现扩展接口来快速地定制逻辑。例如定制规则管理、适配动态数据源等。



### Sentinel 的主要特性：

<img src="img/Sentinel 的主要特性.png" style="zoom:25%;" />



### Sentinel 分为两个部分:

- 核心库（Java 客户端）不依赖任何框架/库，能够运行于所有 Java 运行时环境，同时对 Dubbo/Spring Cloud 等框架也有较好的支持。
- 控制台（Dashboard）基于 Spring Boot 开发，打包后可以直接运行，不需要额外的 Tomcat 等应用容器。

---

## Sentinel 基本概念

Sentinel 实现限流、隔离、降级、熔断功能，本质要做的就是两件事：

- 统计数据：统计某个资源的访问数据，例如：QPS、RT 等信息；
- 规则判断：判断限流规则、隔离规则、降级规则、熔断规则是否满足。



### 资源

- 资源是 Sentinel 的关键概念。它可以是 Java 应用程序中的任何内容，例如，由应用程序提供的服务，或由应用程序调用的其它应用提供的服务，甚至可以是一段代码。

- 只要通过 Sentinel API 定义的代码，就是资源，能够被 Sentinel 保护起来。大部分情况下，可以使用方法签名，URL，甚至服务名称作为资源名来表示资源。

- **资源就是希望被 Sentinel 保护的业务，例如项目中定义的 Controller 方法，就是默认被 Sentinel 保护的资源**



### 规则

围绕资源的实时状态设定的规则，可以包括**流量控制规则**、**熔断降级规则**以及**系统保护规则**。所有规则可以动态实时调整。

---

## Sentinel 工作原理

### Sentinel 的架构图

<img src="img/Sentinel的架构图.png" style="zoom:20%;" />



### Sentinel 核心类解析

#### ProcessorSlotChain

Sentinel 的核心骨架是 ProcessorSlotChain，这个类基于责任链模式来设计，将不同的功能（限流、降级、系统保护等）封装为一个一个的 Slot，请求进入后逐个执行即可。系统会为每个资源创建一套 SlotChain。SlotChain 其实可以分为两部分：统计数据构建部分（statistic）和判断部分（rule checking）

责任链中的 SlotChain 分为两大类：

1. 统计数据构建部分（statistic）
    - NodeSelectorSlot：负责构建簇点链路中的节点（DefaultNode），将这些节点形成链路树
    - ClusterBuilderSlot：负责构建某个资源的 ClusterNode，ClusterNode 可以保存资源的运行信息以及来源信息（origin 名称），例如响应时间、QPS、block 数量、线程数、异常数等
    - StatisticSlot：负责统计实时调用数据，包括运行信息、来源信息等
2. 规则判断部分（rule checking）
    - AuthoritySlot：负责授权规则（来源控制）
    - SystemSlot：负责系统保护规则
    - ParamFlowSlot：负责热点参数限流规则
    - FlowSlot：负责限流规则
    - DegradeSlot：负责降级规则

各 Slot 在责任链中的顺序图

<img src="img/Slot 顺序.jpg" style="zoom: 33%;" />





#### Context

Context 是对资源操作的上下文，每个资源操作必须属于一个 Context。如果代码中没有指定 Context，则会创建一个 name 为 sentinel_default_context 的默认 Context。一个 Context 生命周期中可以包含多个资源操作。Context 生命周期中最后一个资源在 exit() 时会清理该 Context，也就意味着这个 Context 生命周期结束了。

- Context 代表调用链路上下文，贯穿一次调用链路中的所有资源（Entry）。Context 维持着入口节点（entranceNode）、本次调用链路的 curNode、调用来源（origin）等信息；
- Context 维持的方式：通过 ThreadLocal 传递，只有在入口 enter 的时候生效。由于 Context 是通过 ThreadLocal 传递的，因此对于异步调用链路，线程切换的时候会丢掉 Context，因此需要手动通过 `ContextUtil.runOnContext(context, f)` 来变换 context；
- 后续的 Slot 都可以通过 Context 拿到 DefaultNode 或者 ClusterNode，从而获取统计数据，完成规则判断；
- Context 初始化的过程中，会创建 EntranceNode、contextName 就是 EntranceNode 的名称

```java
public void contextDemo() {
    // 创建一个来自于 appA 访问的 Context，”entranceOne“ 为 Context 的名称，”appA“ 为来源名称
    ContextUtil.enter("entranceOne", "appA");
    
    // Entry 就是一个资源操作对象
    Entry resource1 = null;
    Entry resource2 = null;
    
    try {
        // 获取资源 resource1 的 entry
        resource1 = SphU.entry("resource1");
        // 代码至此，说明当前对资源 resource1 的请求通过了流控
        // 对资源 resource1 的相关业务处理。。。

        // 获取资源 resource2 的 entry
        resource2 = SphU.entry("resource2");
        // 代码至此，说明当前对资源 resource2 的请求通过了流控
        // 对资源 resource2 的相关业务处理。。。
    } catch (BlockException e) {
        // 代码至此，说明请求被限流，这里执行降级处理
    } finally {
        if (resource1 != null) {
            resource1.exit();
        }
        if (resource2 != null) {
            resource2.exit();
        }
    }
    // 释放 name 为 entranceOne 的 Context
    ContextUtil.exit();

    // --------------------------------------------------------

    // 创建另一个来自于 appA 访问的 Context，
    // entranceTwo 为 Context 的 name
    ContextUtil.enter("entranceTwo", "appA");
    
    // Entry 就是一个资源操作对象
    Entry resource3 = null;
    
    try {
        // 获取资源 resource2 的 entry
        resource2 = SphU.entry("resource2");
        // 代码至此，说明当前对资源 resource2 的请求通过了流控
        // 对资源 resource2 的相关业务处理。。。


        // 获取资源 resource3 的 entry
        resource3 = SphU.entry("resource3");
        // 代码至此，说明当前对资源 resource3 的请求通过了流控
        // 对资源 resource3 的相关业务处理。。。

    } catch (BlockException e) {
        // 代码至此，说明请求被限流，这里执行降级处理
    } finally {
        if (resource2 != null) {
            resource2.exit();
        }
        if (resource3 != null) {
            resource3.exit();
        }
    }
    // 释放 name 为 entranceTwo 的 Context
    ContextUtil.exit();
}
```



#### Entry

默认情况下，Sentinel 会将 Controller 中的方法作为被保护资源，而资源用 Entry 来表示：

- 每一次资源调用都会创建一个 Entry。Entry 包含了资源名、curNode（当前统计节点）、originNode（来源统计节点）等信息。

- CtEntry 为普通的 Entry，在调用 `SphU.entry(xxx)` 的时候创建。特性：Linked entry within current context（内部维护着 parent 和 child）

- **需要注意的一点**：CtEntry 构造函数中会做**调用链的变换**，即将当前 Entry 接到传入 Context 的调用链路上（setUpEntryFor）。

- 资源调用结束时需要 `entry.exit()`。exit 操作会过一遍 slot chain exit，恢复调用栈，exit context 然后清空 entry 中的 context 防止重复调用。

```java
public abstract class Entry implements AutoCloseable {
    private static final Object[] OBJECTS0 = new Object[0];
    private final long createTimestamp;
    private long completeTimestamp;
    private Node curNode;
    private Node originNode;
    ……
}

class CtEntry extends Entry {
    protected Entry parent = null;
    protected Entry child = null;
    protected ProcessorSlot<Object> chain;
    protected Context context;
    protected LinkedList<BiConsumer<Context, Entry>> exitHandlers;
    
    CtEntry(ResourceWrapper resourceWrapper, ProcessorSlot<Object> chain, Context context) {
        super(resourceWrapper);
        this.chain = chain;
        this.context = context;
		
        // 调用链的变换，即将当前 Entry 接到传入 Context 的调用链路上
        setUpEntryFor(context);
    }
    ……
}

// 声明 Entry 的 API 示例
try{
    /**
     * 创建 Entry，并应用流控规则
     * 资源名可以使用任意有业务语义的字符串，比如方法名、接口名或其它可唯一标识的字符串
     */
    Entry entry = Sphu.entry("resourceName");
    
    // 至此，流控规则应用完毕，执行目标方法，即执行被保护的业务逻辑。
    pjp.proceed();
        
} catch(){
	// 资源访问阻止，被限流或被降级，在此处进行相应的处理操作
} finally{
    // 清空 entry 中的 context 防止重复调用
    entry.exit();
}
```

Context 的初始化：

spring-cloud-starteralibaba--sentinel → spring.facotries → SentinelWebAutoConfiguration → @Bean SentinelWebInterceptor → 继承 AbstractSentinelInterceptor → 实现了 HandlerInterceptor → HandlerInterceptor 会拦截所有进入 Controller 的方法，执行 preHandle 前置拦截方法，而 Context 的初始化就是在 preHandle 中完成的。



#### Node

簇点链路：就是项目内的调用链路，链路中被监控的每个接口就是一个资源。默认情况下 sentinel 会监控 SpringMVC 的每一个端点（Endpoint），因为 SpringMVC 的每一个端点就是调用链路中的一个资源。流控、熔断等都是针对簇点链路中的资源来设置的。

Sentinel 里面的各种种类的统计节点，即簇点链路是由一个个 Node 组成的：

<img src="img/ClusterNode、EntranceNode、DefaultNode 和 StatisticNode 之间的关系.jpg" style="zoom: 25%;" />

所有的节点都可以记录对资源的访问统计数据，所以都是 StatisticNode 的子类，StatisticNode 是最为基础的统计节点，包含秒级和分钟级两个滑动窗口结构。

```java
/**
 * 定义了一个使用数组保存数据的计量器（以"秒"为单位）
 * SAMPLE_COUNT：样本窗口数量，默认值为 2
 * INTERVAL：时间窗长度，默认值为 1000 毫秒
 */
private transient volatile Metric rollingCounterInSecond = new ArrayMetric(SampleCountProperty.SAMPLE_COUNT,
    IntervalProperty.INTERVAL);

/**
 * 定义了一个使用数组保存数据的计量器（以"分"为单位）
 */
private transient Metric rollingCounterInMinute = new ArrayMetric(60, 60 * 1000, false);
```

节点 Node 按照作用分为以下几类：

1. DefaultNode：链路节点，用于统计调用链路上某个资源的数据，维持树状结构，即代表链路树中的每一个资源。一个资源出现在不同链路中时，会创建不同的 DefaultNode 节点
2. ClusterNode：簇点，代表资源，一个资源不管出现在多少链路中，只会有一个 ClusterNode，用于记录当前资源被访问的所有统计数据之和。即用于统计每个资源全局的数据（不区分调用链路），以及存放该资源的按来源区分的调用数据（类型为 `StatisticNode`）。特别是 `Constants.ENTRY_NODE` 节点用于统计全局的入口资源数据
3. EntranceNode：入口节点，特殊的链路节点（DefaultNode），对应某个 Context 入口的所有调用数据。`Constants.ROOT` 节点也是入口节点。

DefaultNode 记录的是资源在当前链路中的访问数据，用来实现基于链路模式的限流规则。ClusterNode 记录的是资源在所有链路中的访问数据，实现默认模式、关联模式的限流规则。

例如一个 MVC 项目中，两个业务接口：

- 业务 1：Controller 中的资源 /order/query 访问了 Service 中的资源 /cat
- 业务 2：Controller 中的资源 /order/save 访问了 Service 中的资源 /cat

创建的链路图如下：

<img src="img/Node 间关系图.jpg" style="zoom: 15%;" />

节点的构建的时机：

- **EntranceNode** 在 `ContextUtil.enter(xxx)` 的时候就创建了，然后塞到 **Context** 里面。
- **NodeSelectorSlot**：根据 **context** 创建 **DefaultNode**，然后 `set curNode to context`。
- **ClusterBuilderSlot**：首先根据 **resourceName** 创建 **ClusterNode**，并且 `set clusterNode to defaultNode`；然后再根据 **origin** 创建来源节点（类型为 **StatisticNode**），并且` set originNode to curEntry`。

几种 Node 的维度（数目）：

- ClusterNode 的维度是 resource
- DefaultNode 的维度是 resource * context，存在每个 NodeSelectorSlot 的 map 里面
- EntranceNode 的维度是 context，存在 ContextUtil 类的 contextNameNodeMap 里面
- 来源节点（类型为 StatisticNode）的维度是 resource * origin，存在每个 ClusterNode 的 originCountMap 里面

Node 节点总结：

- Node：用于完成数据统计的接口；
- StatisticNode：统计节点，是 Node 接口的实现类，用于完成数据统计；
- EntranceNode：入口节点，一个 Context 会有一个入口节点，用于统计当前 Context 的总体流量数据；
- DefaultNode：默认节点，用于统计一个资源在当前 Context 中的流量数据；
- ClusterNode：集群节点，用于统计一个资源在所有 Context 中的总体流量数据；



### SPI 机制

Sentinel 槽链中各 Slot 的执行顺序是固定好的。但并不是绝对不能改变的。Sentinel 将 ProcessorSlot 作为 SPI 接口进行扩展，使得 SlotChain 具备了扩展能力。用户可以自定义 Slot，并编排 Slot 间的顺序，从而可以给 Sentinel 添加自定义的功能。

<img src="img/自定义Slot.png" style="zoom:35%;" />



### Slot 简介

#### NodeSelectorSlot

调用链路构建

负责收集资源的路径，并将这些资源的调用路径，以树状结构存储起来，用于根据调用路径来限流降级。

```java
ContextUtil.enter("entrance1", "appA");
 Entry nodeA = SphU.entry("nodeA");
 if (nodeA != null) {
    nodeA.exit();
 }
 ContextUtil.exit();
```

上述代码通过 `ContextUtil.enter()` 创建了一个名为 `entrance1` 的上下文，同时指定调用发起者为 `appA`；接着通过 `SphU.entry()`请求一个 token，如果该方法顺利执行没有抛 `BlockException`，表明 token 请求成功。

以上代码将在内存中生成以下结构：

```txt
 	     	machine-root
                 /     
                /
         EntranceNode1
              /
             /   
      DefaultNode(nodeA)
```

注意：每个 `DefaultNode` 由资源 ID 和输入名称来标识。换句话说，一个资源 ID 可以有多个不同入口的 DefaultNode。

```java
ContextUtil.enter("entrance1", "appA");
  Entry nodeA = SphU.entry("nodeA");
  if (nodeA != null) {
    nodeA.exit();
  }
  ContextUtil.exit();

ContextUtil.enter("entrance2", "appA");
  nodeA = SphU.entry("nodeA");
  if (nodeA != null) {
    nodeA.exit();
  }
  ContextUtil.exit();
```

以上代码将在内存中生成以下结构：

```txt
                   machine-root
                   /         \
                  /           \
          EntranceNode1   EntranceNode2
                /               \
               /                 \
       DefaultNode(nodeA)   DefaultNode(nodeA)
```

上面的结构可以通过调用 `curl http://localhost:8719/tree?type=root` 来显示：

```txt
EntranceNode: machine-root(t:0 pq:1 bq:0 tq:1 rt:0 prq:1 1mp:0 1mb:0 1mt:0)
-EntranceNode1: Entrance1(t:0 pq:1 bq:0 tq:1 rt:0 prq:1 1mp:0 1mb:0 1mt:0)
--nodeA(t:0 pq:1 bq:0 tq:1 rt:0 prq:1 1mp:0 1mb:0 1mt:0)
-EntranceNode2: Entrance1(t:0 pq:1 bq:0 tq:1 rt:0 prq:1 1mp:0 1mb:0 1mt:0)
--nodeA(t:0 pq:1 bq:0 tq:1 rt:0 prq:1 1mp:0 1mb:0 1mt:0)

t:threadNum  pq:passQps  bq:blockedQps  tq:totalQps  rt:averageRt  prq: passRequestQps 1mp:1m-passed 1mb:1m-blocked 1mt:1m-total
```



#### ClusterBuilderSlot

统计簇点构建

用于存储资源的统计信息以及调用者信息，例如该资源的 RT，QPS，thread count，Block count，Exception count 等等，这些信息将用作为多维度限流，降级的依据。简单来说，就是用于构建 ClusterNode。



#### StatisticSlot

监控统计

StatisticSlot 是 Sentinel 最为重要的类之一，用于根据规则判断结果进行相应的统计操作。用于记录、统计不同纬度的 runtime 指标监控信息。

执行 entry() 方法的时候：依次执行后面的判断 slot。每个 slot 触发流控的话会抛出异常（`BlockException` 的子类）。若有 `BlockException` 抛出，则记录 block 数据；若无异常抛出则算作可通过（pass），记录 pass 数据。

执行 exit() 方法的时候：若无 error（无论是业务异常还是流控异常），记录 complete（success）以及 RT，线程数-1。

记录数据的维度：线程数+1、记录当前 DefaultNode 数据、记录对应的 originNode 数据（若存在 origin）、累计 IN 统计数据（若流量类型为 IN）。

`StatisticSlot` 是 Sentinel 的核心功能插槽之一，用于统计实时的调用数据。

- `clusterNode`：资源唯一标识的 ClusterNode 的实时统计
- `origin`：根据来自不同调用者的统计信息
- `defaultnode`: 根据入口上下文区分的资源 ID 的 runtime 统计
- 入口流量的统计

Sentinel 底层采用高性能的滑动窗口数据结构 `LeapArray` 来统计实时的秒级指标数据，可以很好地支撑写多于读的高并发场景。

<img src="img/滑动窗口数据结构 LeapArray.png" style="zoom:25%;" />



#### ParamFlowSlot

热点参数限流，对应**热点流控**。

热点参数限流是分别统计参数值相同的请求，判断是否超过 QPS 阈值。

例如图中所设置的选项：

1. 对 hot 这个资源的 0 号参数（第一个参数）做统计，每 1 秒相同参数值的请求数不能超过 5 次
2. 对 0 号的 long 类型参数限流，每 1 秒相同参数的 QPS 值不能超过 5，有两个例外：
    1. 如果参数值是 100，则每 1 秒允许的 QPS 为 10
    2. 如果参数值是 101，则每 1 秒允许的 QPS 为 15

<img src="img/热点规则.jpg" style="zoom: 25%;" />



#### FlowSlot

用于根据预设的限流规则以及前面 slot 统计的状态，来进行流量控制。对应**流控规则**。

这个 slot 主要根据预设的资源的统计信息，按照固定的次序，依次生效。如果一个资源对应两条或者多条流控规则，则会根据如下次序依次检验，直到全部通过或者有一个规则生效为止:

三种流控模式：

- 直接模式：统计当前资源的请求，触发阈值时对当前资源直接限流，也是默认的模式
- 关联模式：统计与当前资源相关的另一个资源，触发阈值时，对当前资源限流
- 链路模式：统计从指定链路访问到本资源的请求，触发阈值时，对指定链路限流

三种流控模式，从底层数据统计角度来看，分为两类

1. 对进入资源的所有请求(ClusterNode)做限流统计：直接模式、关联模式
2. 对进入资源的部分链路(DefaultNode)做限流统计：链路模式



三种流控效果：

- 快速失败：达到阈值后，新的请求会被立即拒绝并抛出 FlowException 异常。是默认的处理方式
- warm up：预热模式，对超出阈值的请求同样是拒绝并抛出异常。但这种模式阈值会动态变化，从一个较小值逐渐增加到最大阈值。
- 排队等待：让所有的请求按照先后次序排队执行，两个请求的间隔不能小于指定时长

三种流控效果，从限流算法来看，分为两类

1. 滑动时间窗口算法：快速失败、warm up
2. 漏桶算法：排队等待结果



<img src="img/流控规则.jpg" style="zoom:20%;" />



#### AuthoritySlot

来源访问控制

根据配置的黑白名单和调用来源信息，来做黑白名单控制。对应**授权规则** 。

- 白名单：来源（origin）在白名单内的调用者允许访问
- 黑名单：俩与（origin）在黑名单内的调用者不允许访问

<img src="img/授权规则.jpg" style="zoom:20%;" />



#### DegradeSlot

熔断降级

通过统计信息及预设的规则，来做熔断，对应降级规则。

熔断降级是解决雪崩问题的重要手段，其思路是由断路器统计服务调用的异常比例、慢请求比例，如果超出阈值则会熔断该服务。即拦截访问该服务的一切请求，而当服务恢复时，断路器会放行访问该服务的请求

<img src="img/断路器.jpg" style="zoom: 33%;" />

断路器熔断策略有三种：慢调用、异常比例、异常数

- 慢调用：业务的响应时长（RT）大于指定时长的请求被认定为慢调用请求。在指定时间内，如果请求数量超过设定的最小数量，慢调用比例大于设定的阈值，则触发熔断。例如下图配置，RT 超过 500ms 的调用是慢调用，统计最近 10000ms 内的请求，如果请求量超过 10 次，并且慢调用比例不低于 0.5，则触发熔断，熔断时长为 5s，然后进入 half-open 状态，放行一次请求做测试

    <img src="img/慢调用熔断规则.jpg" style="zoom: 33%;" />

- 异常比例或异常数：统计指定时间内的调用，如果调用次数超过指定请求数，并且出现异常的比例达到设定的比例阈值（或超过指定异常数），则触发熔断。例如下图配置，统计最近 1000ms 内的请求，如果请求超过 10 次，并且异常比例不低于 0.4，则触发熔断，熔断时长为 5s，然后进入 half-open 状态，放行一次请求做测试

    <img src="img/异常比例熔断规则.jpg" style="zoom: 33%;" />



#### SystemSlot

系统保护

通过系统的状态，例如 load1 等，来控制总的入口流量。对应**系统规则**。

这个 slot 会根据对于当前系统的整体情况，对入口资源的调用进行动态调配。其原理是让入口的流量和当前系统的预计容量达到一个动态平衡。

注意系统规则只对入口流量起作用（调用类型为 `EntryType.IN`），对出口流量无效。可通过 `SphU.entry(res, entryType)` 指定调用类型，如果不指定，默认是 `EntryType.OUT`。

<img src="img/系统保护规则.jpg" style="zoom:20%;"/>



## Sentinel 核心源码解析

<img src="img/Sentinel核心源码解析流程图.png" style="zoom:;" />



## 滑动时间窗算法

对于滑动时间窗算法的源码解析分为两部分：对数据的统计，与对统计数据的使用。

### 时间窗限流算法

#### 算法原理

<img src="img/时间窗限流算法原理.png" style="zoom: 33%;" />

- 该算法原理是，系统会自动选定一个时间窗口的起始零点，然后按照固定长度将时间轴划分为若干定长 的时间窗口。所以该算法也称为“固定时间窗算法”。

- 当请求到达时，系统会查看该请求到达的时间点所在的时间窗口当前统计的数据是否超出了预先设定好 的阈值。未超出，则请求通过，否则被限流。



#### 存在的问题

<img src="img/时间窗限流算法存在的问题.png" style="zoom:33%;" />

该算法存在这样的问题：连续两个时间窗口中的统计数据都没有超出阈值，但在跨窗口的时间窗长度范 围内的统计数据却超出了阈值。



### 滑动时间窗限流算法

#### 算法原理

<img src="img/滑动时间窗限流算法原理.png" style="zoom:33%;" />

滑动时间窗限流算法解决了固定时间窗限流算法的问题。其没有划分固定的时间窗起点与终点，而是将 每一次请求的到来时间点作为统计时间窗的终点，起点则是终点向前推时间窗长度的时间点。这种时间 窗称为“滑动时间窗”。



#### 存在的问题

<img src="img/滑动时间窗限流算法存在的问题.png" style="zoom:33%;" />



#### 算法改进

<img src="img/滑动时间窗限流算法的改进.png" style="zoom:33%;" />

- 针对以上问题，系统采用了一种“折中”的改进措施：将整个时间轴拆分为若干“样本窗口”，样本窗口的 长度是小于滑动时间窗口长度的。当等于滑动时间窗口长度时，就变为了“固定时间窗口算法”。 一般 时间窗口长度会是样本窗口长度的整数倍。

- 那么是如何判断一个请求是否能够通过呢？当到达样本窗口终点时间时，每个样本窗口会统计一次本样 本窗口中的流量数据并记录下来。当一个请求到达时，会统计出当前请求时间点所在样本窗口中的流量 数据，然后再获取到当前请求时间点所在时间窗中其它样本窗口的统计数据，求和后，如果没有超出阈 值，则通过，否则被限流。



### 数据统计源码解析

<img src="img/Sentinel滑动时间窗算法源码解析—数据统计.png"/>



### 使用统计数据

<img src="img/Sentinel滑动时间窗算法源码解析—使用统计数据.png" />

---

## Sentinel 持久化

### Sentinel 持久化的模式

1. 原始模式，数据只保存在内存中，Dashboard 的推送规则方式是通过 API 将规则推送至客户端并直接更新到内存中。服务重启后，数据会丢失。

2. Pull 模式，数据由控制台推送给客户端，客户端扩展写数据源(WritableDataSource)，将数据写入某个文件、数据库或配置中心，同时，客户端负责定期轮询从文件、数据库或配置中心拉取数据，此种方式可以保证服务重启后，数据不会丢失，但无法保证数据的一致性和实时性，并且拉取频繁的话可能还会出现新能问题。

    > Pull 模式的数据源（如本地文件、RDBMS 等）一般是可写入的。使用时需要在客户端注册数据源：将对应的读数据源注册至对应的 RuleManager，将写数据源注册至 transport 的 WritableDataSourceRegistry 中。
    >
    > 首先 Sentinel 控制台通过 API 将规则推送至客户端并更新到内存中，接着注册的写数据源会将新的规则保存到本地的文件中。使用 pull 模式的数据源时一般不需要对 Sentinel 控制台进行改造。这种实现方法好处是简单，坏处是无法保证监控数据的一致性。 
    >
    > 数据源的加载和初始化，可以通过 Sentinel 的 SPI 机制进行加载，即实现 InitFunc 接口，在这个实现类中实现数据源的创建等相关逻辑。

3. Push 模式，数据由控制台推送值配置中心，配置中心统一推送给客户端，客户端扩展读数据源(ReadableDataSource)，通过注册监听器的方式时刻监听配置中心的数据变化，能更好的保证数据的实时性和一致性，但此种方式 sentinel 未做实现，需第三方实现。

    > Sentinel Dashboard 监听 Nacos 配置的变化，如发生变化就更新本地缓存。在 Sentinel Dashboard 端新增或修改规则配置在保存到内存的同时，直接发布配置到 nacos 配置中心；Sentinel Dashboard 直接从 nacos 拉取所有的规则配置。Sentinel Dashboard 和 Sentinel client 不直接通信，而是通过 nacos 配置中心获取到配置的变更。
    >
    > 从 Sentinel 1.4.0 开始，Sentinel 控制台提供 DynamicRulePublisher 和 DynamicRuleProvider 接口用于实现应用维度的规则推送和拉取：
    >
    > - DynamicRulePublisher<T>：推送规则
    > - DynamicRuleProvider<T>：拉取规则



### Naocs 配置中心实现 Push 模型

#### sentinel-dashboard 端改造

sentinel-dashboard 模块针对每种流控规则分别实现 DynamicRulePublisher 接口和 DynamicRuleProvider 接口。用于与 Nacos 配置中心通信，实现流控规则数据推送至配置中心和从配置中心拉取流控规则数据。

```java
// 用于向配置中心推送数据
@Component("flowRuleNacosPublisher")
public class FlowRuleNacosPublisher implements DynamicRulePublisher<List<FlowRuleEntity>> {

    @Autowired
    private ConfigService configService;

    @Override
    public void publish(String app, List<FlowRuleEntity> rules) throws Exception {
        AssertUtil.notEmpty(app,"app name cannot be empty");

        if (rules == null){
            return;
        }

        // 发布配置到 nacos 配置中心
        configService.publishConfig(app+ NacosConfigUtil.FLOW_DATA_ID_POSTFIX,
                NacosConfigUtil.GROUP_ID,NacosConfigUtil.convertToRule(rules));
    }
}

// 用于从数据中心拉取数据
@Component("flowRuleNacosProvider")
public class FlowRuleNacosProvider implements DynamicRuleProvider<List<FlowRuleEntity>> {

    @Autowired
    private ConfigService configService;

    @Override
    public List<FlowRuleEntity> getRules(String appName,String ip,Integer port) throws NacosException {

        // 从 nacos 配置中心拉取配置
        String rules = configService.getConfig(appName + NacosConfigUtil.FLOW_DATA_ID_POSTFIX,
                NacosConfigUtil.GROUP_ID, NacosConfigUtil.READ_TIMEOUT);

        if (StringUtil.isEmpty(rules)){
            return new ArrayList<>();
        }

        // 解析 json 获取到 List<FlowRule>
        List<FlowRule> list = JSON.parseArray(rules, FlowRule.class);

        // 客户端规则实体是：FlowRule ===> 控制台规则实体是：FlowRuleEntity
        return list
                .stream()
                .map(rule -> FlowRuleEntity.fromFlowRule(appName,ip,port,rule))
                .collect(Collectors.toList());
    }
}
```

然后改造控制台的规则处理器，即 sentinel-dashboard 模块的 controller 包中的各种处理器，例如流控规则处理器，FlowControllerV1，其内有处理流控规则的增删改查的方法，在这些方法内，使用前面创建的组件，替换相应位置的逻辑，以查询流控规则列表和新增流控规则为例：

```java
@Autowired
@Qualifier("flowRuleNacosProvider")
private DynamicRuleProvider flowRuleNacosProvider;

@Autowired
@Qualifier("flowRuleNacosPublisher")
private DynamicRulePublisher flowRuleNacosPublisher;

/**
 * 查询流控规则列表
 */
@GetMapping("/rules")
@AuthAction(PrivilegeType.READ_RULE)
public Result<List<FlowRuleEntity>> apiQueryMachineRules(@RequestParam String app,@RequestParam String ip,@RequestParam Integer port) {

    // ……
    
    try {
        // List<FlowRuleEntity> rules = sentinelApiClient.fetchFlowRuleOfMachine(app, ip, port);

        // 从配置中心获取规则配置
        List<FlowRuleEntity> rules = (List<FlowRuleEntity>) flowRuleNacosProvider.getRules(app, ip, port);

        if (rules !=null && !rules.isEmpty()){
            for (FlowRuleEntity entity : rules) {
                entity.setApp(app);
                if (entity.getClusterConfig() != null && entity.getClusterConfig().getFlowId() != null){
                    entity.setId(entity.getClusterConfig().getFlowId());
                }
            }
        }

        rules = repository.saveAll(rules);
        return Result.ofSuccess(rules);
    } catch (Throwable throwable) {
        logger.error("Error when querying flow rules", throwable);
        return Result.ofThrowable(-1, throwable);
    }
}

/**
 * 控制台新增流控规则，并将此推送流控规则至客户端
 * @param entity 流控规则数据会被封装成 FlowRuleEntity 对象
 */
@PostMapping("/rule")
@AuthAction(PrivilegeType.WRITE_RULE)
public Result<FlowRuleEntity> apiAddFlowRule(@RequestBody FlowRuleEntity entity) {
    /**
     * 校验流控规则实体，流控规则数据会被封装成 FlowRuleEntity 对象
     */
    Result<FlowRuleEntity> checkResult = checkEntityInternal(entity);
    if (checkResult != null) {
        return checkResult;
    }
    entity.setId(null);
    Date date = new Date();
    entity.setGmtCreate(date);
    entity.setGmtModified(date);
    entity.setLimitApp(entity.getLimitApp().trim());
    entity.setResource(entity.getResource().trim());
    try {
        /**
         * 控制台保存流控规则
         * 扩展点：可保存在 mysql、nacos config、redis……
         */
        entity = repository.save(entity);

        /**
         * 控制台向 Nacos 配置中心推送新增的流控规则
         */
        publishRules(entity.getApp());
        return Result.ofSuccess(entity);
    } catch (Throwable t) {
        Throwable e = t instanceof ExecutionException ? t.getCause() : t;
        logger.error("Failed to add new flow rule, app={}, ip={}", entity.getApp(), entity.getIp(), e);
        return Result.ofFail(-1, e.getMessage());
    }
}

private void publishRules(/*@NonNull*/ String app) throws Exception {
    List<FlowRuleEntity> rules = repository.findAllByApp(app);
    // 控制台向 Nacos 配置中心推送新增的流控规则
    flowRuleNacosPublisher.publish(app, rules);
}
```

控制台配置 nacos 配置中心地址

```java
@Configuration
public class NacosConfig {

    @Value("${sentinel.nacos.config.serverAddr}")
    private String serverAddr;

    @Bean
    public ConfigService nacosConfigService() throws Exception {
        return ConfigFactory.createConfigService(serverAddr);
    }
}
```



#### 客户端配置

引入 sentinel 数据源扩展包依赖

```xml
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-datasource-nacos</artifactId>
</dependency>
```

配置文件配置 sentinel 相关配置

```yaml
server:
  port: 8081

spring:
  application:
    name: colin-consumer
  cloud:
    nacos:
      discovery:
        server-addr: 47.100.54.86:8488
    sentinel:
      transport:
        # sentinel 的控制台地址
        dashboard: localhost:8888
      datasource:
        flow-rules:
          nacos:
            server-addr: 47.100.54.86:8488
            data-id: colin-consumer-flow-rules
            group-id: SENTINEL_GROUP
            data-type: json
            rule-type: flow
        # 其它流控规则……
```



