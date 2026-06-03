plugins {
    id("tateyokopdf.java-conventions")
    id("tateyokopdf.test-conventions")
    id("tateyokopdf.quality-conventions")
}

dependencies {
    // Ports speak the domain vocabulary (records/enums) but stay free of any adapter library.
    implementation(project(":domain"))
}
