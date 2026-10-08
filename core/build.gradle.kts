plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    implementation("com.google.code.gson:gson:2.12.1")
    implementation("org.jsoup:jsoup:1.23.2")
    testImplementation("junit:junit:4.13.2")
}
