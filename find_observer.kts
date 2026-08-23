import java.util.zip.ZipFile
import java.io.File

fun main() {
    val createJarPath = "C:\\Users\\jannis\\.gradle\\caches\\modules-2\\files-2.1\\com.simibubi.create\\create-1.21.1\\6.0.10-280\\5693ff29ed16b32ad51cf57b92f707f15b6d1ee5\\create-1.21.1-6.0.10-280-slim.jar"
    val zip = ZipFile(createJarPath)
    val entries = zip.entries()
    while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        if (entry.name.contains("trains") && entry.name.contains("bserver") && entry.name.endsWith(".class")) {
            println(entry.name)
        }
    }
}
