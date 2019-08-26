android {
  buildTypes {
    create("xyz") {
      applicationIdSuffix("mySuffix")
      buildConfigField("abcd", "efgh", "ijkl")
      consumerProguardFiles("proguard-android.txt", "proguard-rules.pro")
      debuggable(true)
      embedMicroApp(true)
      jniDebuggable(true)
      manifestPlaceholders(mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"))
      minifyEnabled(true)
      multiDexEnabled(true)
      proguardFiles("proguard-android.txt", "proguard-rules.pro")
      pseudoLocalesEnabled(true)
      renderscriptDebuggable(true)
      renderscriptOptimLevel(1)
      resValue("mnop", "qrst", "uvwx")
      shrinkResources(true)
      testCoverageEnabled(true)
      useJack(true)
      versionNameSuffix("abc")
      zipAlignEnabled(true)
    }
  }
}