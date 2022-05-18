Steps to reproduce gradle/gradle#20778

--

1. Clone this repo
2. `$ cd activity`
3. `$ rm -rf ../out/activity-playground/activity-playground/lifecycle/lifecycle-common/build/libs`
4. Clone gradle/gradle
5. Checkout branch `km/75/issue-20778` which contains extra deugging comments
6. Locally install this version of Gradle
7. Using Gradle from step 6, `$ gradle bOS --no-build-cache --no-configuration-cache -i --no-daemon # -Dorg.gradle.debug=true`

To see the androidx code working correctly, revert commit 583291c64269b2b9b71529e136ff28ed9b9c9433.

Useful conditional breakpoints in Gradle:

* DefaultTransformer.java:264 - see the transform executing (and throwing) _very_ early
* DefaultExecutionPlan:641 (in selectNext()): `node instanceof LocalTaskNode && ":lifecycle:lifecycle-livedata-core:compileReleaseJavaWithJavac".equals(((LocalTaskNode) node).getTask().getPath())`

# NB

Observed error:
```
* What went wrong:
Execution failed for task ':lifecycle:lifecycle-livedata-core:compileReleaseJavaWithJavac'.
> Could not resolve all files for configuration ':lifecycle:lifecycle-livedata-core:releaseCompileClasspath'.
   > Failed to transform lifecycle-common.jar (project :lifecycle:lifecycle-common) to match attributes {artifactType=android-classes-jar, org.gradle.category=library, org.gradle.dependency.bundling=external, org.gradle.jvm.version=8, org.gradle.libraryelements=jar, org.gradle.usage=java-api}.
      > Execution failed for IdentityTransform: /home/runner/work/androidx/androidx/out/activity-playground/activity-playground/lifecycle/lifecycle-common/build/libs/lifecycle-common-2.6.0-alpha01.jar.
         > File/directory does not exist: /home/runner/work/androidx/androidx/out/activity-playground/activity-playground/lifecycle/lifecycle-common/build/libs/lifecycle-common-2.6.0-alpha01.jar
```

LKG scan:
https://ge.androidx.dev/s/vgiav7uhe5rki

First break scan:
https://ge.androidx.dev/s/npunic2rxxzja

By the way, this sample is using AGP 7.4.0-alpha01