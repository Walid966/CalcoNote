plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
tasks.register<JavaExec>("verifyCalculator") {
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.hesba.core.CalculatorTestKt")
}
tasks.named("check") { dependsOn("verifyCalculator") }
