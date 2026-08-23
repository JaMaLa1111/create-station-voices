# Kotlin Modding Skeleton
Provides an example mod written in Kotlin using Kotlin for Forge.

## IMPORTANT:
If your mod uses another library that is written in Kotlin (like OkHttp3) you will need to use the slim artifact of KFF instead.
```groovy
dependencies {
    // Use slim artifact of KFF
    implementation('thedarkcolour:kotlinforforge-neoforge:VERSION:slim')
    
    // Add Kotlin libs as non-mc libs in ModDevGradle
    additionalRuntimeClasspath ('org.jetbrains.kotlin:kotlin-stdlib')
    additionalRuntimeClasspath ('org.jetbrains.kotlin:kotlin-reflect')
    additionalRuntimeClasspath ('org.jetbrains.kotlinx:kotlinx-coroutines-core')
    additionalRuntimeClasspath ('org.jetbrains.kotlinx:kotlinx-serialization-core')
    additionalRuntimeClasspath ('org.jetbrains.kotlinx:kotlinx-serialization-json')
}
```

ALSO:  
Debugging with Coroutines is currently broken due to an issue [with JPMS and IntelliJ](https://youtrack.jetbrains.com/issue/KTIJ-15750/Debugger-doesnt-work-at-all-in-Java-projects-with-enabled-Kotlin-plugin-and-coroutine-debugger#focus=Comments-27-4923828.0-0). You might see an error like this upon running the game in Debug Mode:
```
Exception in thread "main" java.lang.NoClassDefFoundError: kotlin/Result
    at kotlinx.coroutines.debug.AgentPremain.<clinit>(AgentPremain.kt:20)
    at java.base/jdk.internal.misc.Unsafe.ensureClassInitialized0(Native Method)
    at java.base/jdk.internal.misc.Unsafe.ensureClassInitialized(Unsafe.java:1160)
    at java.base/jdk.internal.reflect.MethodHandleAccessorFactory.ensureClassInitialized(MethodHandleAccessorFactory.java:300)
    at java.base/jdk.internal.reflect.MethodHandleAccessorFactory.newMethodAccessor(MethodHandleAccessorFactory.java:71)
    at java.base/jdk.internal.reflect.ReflectionFactory.newMethodAccessor(ReflectionFactory.java:159)
    at java.base/java.lang.reflect.Method.acquireMethodAccessor(Method.java:726)
    at java.base/java.lang.reflect.Method.invoke(Method.java:577)
    at java.instrument/sun.instrument.InstrumentationImpl.loadClassAndStartAgent(InstrumentationImpl.java:560)
    at java.instrument/sun.instrument.InstrumentationImpl.loadClassAndCallPremain(InstrumentationImpl.java:572)
Caused by: java.lang.ClassNotFoundException: kotlin.Result
    ... 10 more
*** java.lang.instrument ASSERTION FAILED ***: "!errorOutstanding" with message Outstanding error when calling method in invokeJavaAgentMainMethod at s\open\src\java.instrument\share\native\libinstrument\JPLISAgent.c line: 627
*** java.lang.instrument ASSERTION FAILED ***: "success" with message invokeJavaAgentMainMethod failed at s\open\src\java.instrument\share\native\libinstrument\JPLISAgent.c line: 466
*** java.lang.instrument ASSERTION FAILED ***: "result" with message agent load/premain call failed at s\open\src\java.instrument\share\native\libinstrument\JPLISAgent.c line: 429
```

### The Fix
The link above contains a fix for older IntelliJ versions which no longer applies. The new fix is going to `Settings > Build, Execution, Deployment > Debugger` and ticking the "Disable coroutine agent" setting under the Kotlin section:
![coroutines_fix.png](coroutines_fix.png)