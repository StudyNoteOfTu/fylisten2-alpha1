# 万物皆有灵—— FyListen 生命周期监听框架

**对生命周期进行监听，是解决内存泄漏非常重要的手段之一。**

**万物皆有灵**：只要你认为存在生命周期的对象，都能使用 FyListen 进行生命周期监听，除了 Activity、Fragment、Application、Service等本身就存在生命周期的对象，你也可以认为View、视频播放器、资源下载器等也有生命周期，如视频播放器的生命周期可以视为：初始化->资源加载->播放中->暂停中->暂不播放(可被复用)->关闭结束。**凡是可以定义出生命周期的，你都能使用 FyListen 进行生命周期监听，对代码结构进行优化、处理内存泄漏。**

另外补充的是，**FyListen 是线程安全的**。在多线程环境下，观察者模式的“中间商”管理的资源认为是临界区资源，需要在线程安全的环境下操作，避免监听缺漏、空指针异常等各种问题。

本文从 1）接口、工具类基本介绍；2）实战应用；3）接口详细说明；4）接口方法重名处理办法 进行展开：

**依赖导入：**

Add it in your root build.gradle at the end of repositories:

```css
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

**Step 2.** Add the dependency

```css
dependencies {
    implementation 'com.github.StudyNoteOfTu:fylisten2-alpha1:v1.0.0-alpha1'
}
```

## 0. 接口、工具类介绍

``LifecyclePublisher`` 接口： 

可以被原本就被设计为有生命周期的组件实现：

- ```java
  public class DemoActivity extends Activity implements LifecyclePublisher{...}
  ```

- ```java
  public class DemoView extends View implements LifecyclePublisher{...}
  ```

- ```java
  public class DemoFragment extends Fragment implements LifecyclePublisher{...}
  ```

- ```java
  public class DemoApplication extends Application implements LifecyclePublisher{...}
  ```

也可以被你认为有生命周期的组件实现：

- ```java
  public class MediaPlayer  implements LifecyclePublisher{...}
  ```

``Listener`` 接口：

监听实现了 LifecyclePublisher 接口的类的生命周期，onError()回调设计为必须要实现，当被监听者注册出错等异常情况，可以在 onError() 接口中告诉开发者错误信息。其他的诸如 onAttach()、onCreate()、onDestroy() 等生命周期回调，可以选择你需要的进行实现：

匿名监听者

- ```java
  FyListen.registerListenerSafely(target,new Listener(){
      @Override
      public void onCreate(LifecyclePublisher p){}
      @Override
      public void onResume(LifecyclePublisher p){}
      @Overide
      public void onError(LifecyclePublisher p,String error){}
  })
  ```

具体实现类监听者

- ```java
  public class DemoActivity extends Activity implements LifecyclePublisher{
      @LifecycleTrace(Status.ON_DESTROY)
      @Override
      protected void onDestroy(){}
      
      @LifecycleTrace(Status.ON_RESUME)
      @Override
      protected void onResume(){}
      
      @Override
      protected void onCreate(Bundle savedInstanceState){
          new Thread(()->{
              Downloader downloader = new Downloader();
              //耗时操作，开始去下载资源，并在下载完成后回调更新UI，由于Callback为匿名内部类，持有Activity的引用，可能会造成内存泄漏，所以需要生命周期监听，在activity需要被释放的时候，关闭后台任务
              downloader.beginDownload(new Callback(){...});
              //为下载器添加监听，在onDestroy()时停止下载，释放对Activity的引用
              FyListen.registerListenerAnyway(this,downloader);
          }).start();
      }
      
      private static class Downloader implements Listener{
          public void beginDownload(Callback callback){
              //开启线程下载资源
              //下载完成时：
              callback.onFinish(...);
          }
          public void shutdown(){
              //关闭下载资源的线程，取消下载，结束beginDownload方法，释放callback的引用，
              //也就是释放了对Activity的引用
          }
          @Override
          public void onDestroy(LifecyclePublisher p){
              //关闭下载器
              shutdown();
          }
          @Override
          public void onError(String err){
              //关闭下载器
              shutdown();
          }
      }
  }
  ```



``LifecycleTrace`` 注解：

被 LifecycleTrace 注解的方法被认为是生命周期的回调，只要这个方法被调用，就会通知监听者当前的生命周期。当然前提是实现了 LifecyclePublisher 接口，否则即使加了 LifecycleTrace 注解，也不会被任何人监听到。

它可以被加在本身就有生命周期定义的类上，如Activity：

- ```java
  public class DemoActivity extends Activity implements LifecyclePublisher{
      @LifecycleTrace(Status.ON_DESTROY)
      @Override
      protected void onDestroy(){}
  }
  ```

也可以被加在你认为有生命周期的类上，比如你实现了一个MediaPlayer工具，它有四个阶段：1）初始化；2）加载资源；3）播放素材；4）暂停素材；4）工作结束：

- ```java
  public class MediaPlayer implements LifecyclePublisher{
      @LifecycleTrace(Status.ON_ATTACH)//我选择用ON_ATTACH来定义其正在初始化
      private void onInit(Context context){
          //初始化工具
      }
      
      private void loadSource(Source source){
          //加载资源
          loadFinish();
      }
      
      @LifecycleTrace(Status.ON_CREATE)//我选择用 ON_CREATE 来定义其正在加载资源完成
      private void loadFinish(){
          //加载完成
      }
      
      @LifecycleTrace(Status.ON_RESUME)//我选择用 ON_RESUME 来定义正在播放
      public void play(){
          //播放
      }
      
      @LifecycleTrace(Status.ON_PAUSE)//我选择用 ON_PAUSE 来定义暂停中
      public void pause(){
          //暂停
      }
      
      @LifecycleTrace(Status.ON_STOP)//我选择用 ON_STOP 来定义停止工作
      public void stop(){
          //没有工作，此时当前对象可以放到复用池等待复用
      }
      
      @LifecycleTrace(Status.ON_DESTROY)//我选择用 ON_DESTROY 来定义资源释放
      private void finish(){
          //释放资源
      }
  }
  ```

``FyListen`` 工具使用入口类：

发布者可以通过FyListen进行注册发布，表示自己可以被别人监听由 LifecycleTrace 注解的生命周期

```java
public class LifecyclePublisherImpl implements LifecyclePublisher{
    public LifecyclePublisherImpl(){
        FyListen.registerPublisher(this);
    }
    
    @LifecycleTrace(Status.ON_DESTROY)
    private void finished(){
        //...
    }
}
```

## 1. 使用示例：与RxJava结合，解决内存泄漏问题

使用场景：我使用 RxJava 进行异步数据请求，由于使用了匿名内部类，持有了外部类，例如Activity / Fragment 的引用，如果我再 Activity/Fragment 退出的时候不对异步操作进行关闭，匿名内部类仍然存活，就长时间持有外部类的引用，由此造成内存泄漏。我们通过下面的方法，RxJava+FyListen2，来处理内存泄漏：

如果不使用生命周期监听器，而本身onDestroy()中的业务代码就很多，未来就会不好维护（比如可能要找好久才发现：“哦，我忘记在这个Activity的onDestroy()里面加上 compositeDisposable.dispose()了”）

```java
public class MainActivity extends AppCompatActivity implements LifcyclePublisher{
    CompositeDisposable compositeDisposable;

    @LifecycleTrace(Status.ON_DESTROY)
    @Override
    protected void onDestroy(){
        super.onDestroy();
        //如果不使用生命周期监听器，则要在这里手动添加：
        //compositeDisposable.dispose();
    }

    @Override
    proetected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        Observable.create(emitter->{//匿名内部类
            //模拟请求数据
            Thread.sleep(15000);//耗时15s
            emitter.onNext("user_info");
        }).compose(CommonTransformer.listen(this))
            .compose(new SchedulerTransformer<>())//线程切换
            .subscribe(new Consumer<Object>(){
                @Override
                public void accept(Object o) throws Exception{
                    //拿到请求数据
                    //更新UI
                }
            })
    }
    //监听生命周期，进行dispose()
    private static class CommonTransformer<T> implements Listener,ObservableTransformer<T,T>{
        final CompositeDisposable compositeDisposable = new CompositeDisposable();
        @Override
        public void onDestroy(LifecyclePublisher p){
            if (!compositeDisposable.isDisposed()){
                compositeDisposable.dispose();
            }
        }
        @Override
        public ObservableSource<T> apply(Observable<T> upstream) {
            return upstream.doOnSubscribe(new Consumer<Disposable>() {
                @Override
                public void accept(Disposable disposable) throws Exception {
                    compositeDisposable.add(disposable);
                }
            }); 
        }

        public static <T> CommonTransformer<T> listen(LifecyclePublisher lifecyclePublisher){
            CommonTransformer<T> transformer = new CommonTransformer<>();
            //第一个参数为要监听生命周期的目标，第二个参数为监听者
            FyListen.registerListenerAnyway(lifecyclePublisher,transformer);
            return transformer;
        }
    }

    //线程切换的 transformer
    private static class SchedulerTransformer<T> implements ObservableTransformer<T,T>{

        @Override
        public ObservableSource<T> apply(Observable<T> upstream) {
            return upstream.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
        }
    }
}
```

## 2. 接口详细说明

### 2.1 LifecycleTrace 注解

1. 只能注解在方法上，注解的方法将被 FyListen 认为是生命周期的回调。
2. 传入的参数为：注解的方法被你认为是生命周期的哪一步。

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LifecycleTrace {
    @Status int value();
}
```

3. 所有生命周期的状态标志：

```java
public @interface Status {
    public static final int INITIALED = -1;
    public static final int ON_ATTACH = 0;
    public static final int ON_CREATE =1 ;
    public static final int ON_CREATE_VIEW=2;
    public static final int ON_START=3;
    public static final int ON_RESUME=4;
    public static final int ON_PAUSE=5;
    public static final int ON_STOP=6;
    public static final int ON_DESTROY_VIEW=7;
    public static final int ON_DESTROY=8;
    public static final int ON_DETACH=9;
}
```

## 2.2 LifecyclePublisher 接口

所有你认为有生命周期的类，都可以实现这个空接口。只有实现了该接口，才能被 FyListen 视为生命周期回调发布者。

```java
public interface LifecyclePublisher {
}
```

### 2.3 Listener 接口

监听者的统一规范，你可以监听到发布者公开的全部生命周期，也可以只监听其中几个生命周期。（由 Java8 接口新特性，不强制要求实现接口中所有方法）

```java
public interface Listener {

    default void onAttach(LifecyclePublisher p){}
    default void onCreate(LifecyclePublisher p){}
    default void onCreateView(LifecyclePublisher p){}
    default void onStart(LifecyclePublisher p){}
    default void onResume(LifecyclePublisher p){}
    default void onPause(LifecyclePublisher p){}
    default void onStop(LifecyclePublisher p){}
    default void onDestroy(LifecyclePublisher p){}
    default void onDestroyView(LifecyclePublisher p){}
    default void onDetach(LifecyclePublisher p){}

    //发布者不存在或者死亡，必须重写
    void onError(LifecyclePublisher p,String error);

}
```

### 2.4 FyListen 监听管理着——工具入口

### 2.4.1 注册生命周期发布者

注册了生命周期发布能力的类，可以在对象存活期间，对监听者发布自己的生命周期。你可以在对象的任何生命时机进行注册。刚注册时，发布者状态为 ``Status.INITIALED``，表示还未发布任何生命周期。

1. 通过``FyListen.registerPublisher(LifecyclePublisher o)``主动进行注册:

   Activity 作为生命周期发布者进行注册：

   ```java
   public MainActivity extends Activity implements LifecyclePublisher{
       @Override
       protected void onCreate(Bundle savedInstanceState){
           FyListen.registerPublisher(this);
       }
   }
   ```

   

   ```java
   public MediaPlayer implements LifecyclePublisher{
       //我将MediaPlayer在初始化的时候就进行了发布者注册
       public MediaPlayer(){
           FyListen.registerPublisher(this);
       }
   }
   ```

2. 由监听者通过``FyListen.registerListenerAnyway(LifecyclePublisher target,Listener listener)``强制对未注册的生命周期发布者进行注册，并将监听者自己绑定在其订阅者列表中。这样做的目的是，为确保防止内存泄漏，使用``FyListen.registerListenerAnyway()``监听注册时，强制要求发布者也要注册，避免程序员由于疏忽，忘记将Activivity进行生命周期发布注册。

   ```java
   //RxJava的一个工具类，可能你会把它放在项目的 utils.rxutils 文件夹下。它用于监听生命周期，进行dispose()
   private class CommonTransformer<T> implements Listener,ObservableTransformer<T,T>{
       final CompositeDisposable compositeDisposable = new CompositeDisposable();
       @Override
       public void onDestroy(LifecyclePublisher p){
           if (!compositeDisposable.isDisposed()){
               compositeDisposable.dispose();
           }
       }
       @Override
       public ObservableSource<T> apply(Observable<T> upstream) {
           return upstream.doOnSubscribe(new Consumer<Disposable>() {
               @Override
               public void accept(Disposable disposable) throws Exception {
                   compositeDisposable.add(disposable);
               }
           }); 
       }
   
       public static <T> CommonTransformer<T> listen(LifecyclePublisher lifecyclePublisher){
           CommonTransformer<T> transformer = new CommonTransformer<>();
           //第一个参数为要监听生命周期的目标，第二个参数为监听者
           //为确保防止内存泄漏，使用registerListenerAnyway进行注册，避免程序员由于疏忽，忘记将Activivity进行生命周期发布注册
           FyListen.registerListenerAnyway(lifecyclePublisher,transformer);
           return transformer;
       }
   }
   ```

   

3. 通过修改 ``LifecycleTraceAspect`` 中的 ``needRegisterByDefault=true``让发布者自主进行生命周期发布注册。

   ```java
   public class LifecycleTraceAspect {
       //该功能默认关闭
       public static final boolean needRegisterByDefault = false;
       //...
   }
   ```

   这里需要注意的是:

   - 实现了 LifecyclePublisher 接口的类才能够发布生命周期消息
   - 即使实现了接口，如果发布者没有任何一个方法被注解 ``LifecycleTrace``标记为生命周期方法，它从始至终不会因为(3)的设置而进行发布注册。

### 2.4.2 发布者取消注册，不再主动发布生命周期

你可以通过``FyListen.unregisterPublisher()``来取消注册，并通知其下所有监听者"我不在对外发布我的生命周期"，而后主动放弃所有listener的引用。

```java
public static boolean unregisterPublisher(LifecyclePublisher o) {}
```

### 2.4.3 注册生命周期监听者

实现了 Listener 接口的类，才可以进行生命周期监听注册，成为一个生命周期的监听者，一个监听者可以监听多个生命周期发布者。

1. 使用``FyListen.registerListenerSafely()``进行监听注册。这是个安全模式，如果想监听的生命周期发布者没有进行发布注册，你将监听注册失败，并在``onError()``回调中收到错误信息。该方法有两个重载形式：

   1）添加监听的时候，就获知发布者当前的生命周期状态，即获知发布者最近发布了自己的哪一个生命周期（通过回调获知）：

   ```java
   public static void registerListenerSafely(LifecyclePublisher target, Listener listener,boolean wantCurrentStatusCallback) {}
   ```

   2）添加监听的时候，不需要获知发布者当前的生命周期状态：

   ```java
   public static void registerListenerSafely(LifecyclePublisher target,Listener listener){
       FyListen.registerListenerSafely(target,listener,false);
   }
   ```

2. 使用``FyListen.registerListenerAnyway()``进行监听注册。如果发布者还没有进行生命周期注册发布，这个方法将会强制将该发布者进行发布注册。如果发布者已经注册过，将和``FyListen.registerListenerSafely()``的效果一致。如果注册失败，会在 ``onError`` 回调中收到错误信息。该方法也有两个重载形式：

   添加监听的时候，就获知发布者当前的生命周期状态，与safely方式的区别是，如果发布者是强制注册的，获知的生命周期状态将是 ``INITIALEd``，因为它之前没有发布过任何生命周期通知。

   ```java
   public static void registerListenerAnyway(LifecyclePublisher taget,Listener listener){
       FyListen.registerListenerAnyway(taget, listener,false);
   }
   
   public static void registerListenerAnyway(LifecyclePublisher target,Listener listener,boolean wantCurrentStatusCallback){}
   ```

   

### 2.4.4 监听者取消监听

监听者可以监听多个发布者，也需要取消对其中某一些发布者生命周期的监听，通过``FyListen.unregisterListener(LifecyclePublisher target,Listener listener)``来取消对某一个发布者生命周期的监听：

````java
public static void unregisterListener(LifecyclePublisher target,Listener listener){}
````

监听者本身可能也是一个短声明周期对象，而 FyListen 强引用着自己，为了避免自身的内存泄漏，需要在自己退出之前，通过``FyListen.unregisterListenerAll(Listener listener)``取消所有的监听。

```java
public static void unregisterListenerAll(@NonNull Listener listener){}
```

如果你忽略了监听解绑，也没关系，只要你监听的全都不是长生命/长时间不会被回收的对象，在它们全都被释放的时候，监听者的引用也会被释放。

### 2.4.5 如果你愿意，也可以主动地进行生命周期发布

FyListen中还提供了一个发布者使用的方法``sendLifecycleCallback()``，可以通过代码主动调用，来发布生命周期消息。这个方法很危险，不建议外部随意使用，需要注意的是：

1. 这是个静态方法，没有要求调用者一定是发布者本身
2. 只有当传入的发布者引用是注册过的，才会发布生命周期消息
3. 它是危险的！因为发布生命周期消息会更新 FyListen 对发布者当前生命周期的记录！所以需要使用者**慎重使用**。
4. 好在：发布者如果进入到了新的生命周期，主动发布生命周期通知，还是会更新当前生命周期状态。虽然发布的消息可以是假的，但是并不影响到发布者本身，只影响到监听者们。



## 3. 接口方法重名的解决方式

这是个多态带来的问题。不过，使用 FyListen 时，由于 Listener 的方法都需要传递 LifecyclePublisher 作为参数，即使实现类有同名方法，也由于方法重载的特性，对你的使用不造成影响。

如果你需要解决这种问题，你可以通过桥接思想，通过内部类来处理方法重名问题：

```java 
public class Test{
    interface A{
        boolean f();
    }
    interface B{
        void f();
    }
    //由于方法无法A和B的f()方法在Son中无法重载，无法编译通过
    //public static class Son implement A,B{}
    
    //使用桥接：
    //1.外部类初始化的同时也初始化内部类
    //2.内部类被初始化时获得对外部类的引用
    //3.内部类的重名方法调用到外部类的桥接方法
    //4.桥接用的内部类不应当独立于外部类，所以使用非静态内部类
    //5.如果要写成静态内部类，最好将其设为private保持“只属于外部类"
    //
    //特性：内部类持有外部类时，可以访问到外部类的private变量，也就是Inner和Outter都可以操作 Outter中的资源！从而实现重名不可重载方法的处理。
    public static class Outer implements A{
        public final Inner inner;
        public Outer(){
            inner = new Inner(Outer.this);
        }
        
        public boolean f(){
            System.out.println("f() called by A ref");
        }
        
        //如果SonInner可以单独存在，可以定为静态内部类
        //如果SonInner必须依存外部类，就需要定为非静态内部类
        private class Inner implements B{
            private final Outer outer;
            private Inner(Outer outer){
                this.outer= outer;
            } 
            public void f(){
                System.out.println("f() called by B ref")
            }
        }
    }
}
```

使用桥接：

1. 外部类初始化的同时也初始化内部类
2. 内部类被初始化时获得对外部类的引用
3. 内部类的重名方法调用到外部类的桥接方法
4. 桥接用的内部类不应当独立于外部类，所以使用非静态内部类
5. 如果要写成静态内部类，最好将其设为private保持“只属于外部类”

特性：内部类持有外部类时，可以访问到外部类的private变量，也就是Inner和Outer都可以操作 Outer中的资源！从而实现重名不可重载方法的处理。

既然提到了这个，就说一下如何实现多继承，和上面的实现思路是一样的：

```java
public static abstract class Father1{
    abstract void f();
}

public static abstract class Father2{
    abstract void f();
}

public static class Outer extends Father1{
    private int resource = 0;
    public final Inner inner;

    public Outer() {
        this.inner = new Inner(Outer.this);
    }

    @Override
    void f() {
        resource++;
    }

    private static class Inner extends Father2{
        private final Outer outer;

        private Inner(Outer outer) {
            this.outer = outer;
        }

        @Override
        void f() {
            outer.resource++;
        }
    }
}
```

