import cn.hutool.core.text.csv.CsvReadConfig
import cn.hutool.core.text.csv.CsvUtil
import cn.hutool.crypto.digest.DigestUtil
import cn.hutool.http.HttpUtil
import org.gradle.api.Project
import java.io.File

fun Project.downloadRootCAList() {
    val assets = File(projectDir, "src/main/assets")
    val pem = File(assets, "mozilla_included.pem")
    if (pem.isFile && !requireFlavor().endsWith("Release")) {
        return
    }
    val csv = HttpUtil.get("https://ccadb.my.salesforce-sites.com/mozilla/IncludedCACertificateReportPEMCSV")
    val data = CsvUtil.getReader(CsvReadConfig().setContainsHeader(true)).readFromStr(csv)
    val list = mutableListOf<String>()
    for (row in data) {
        // skip China root CA
        if (row.getByName("Geographic Focus").contains("China")) continue
        if (!row.getByName("Trust Bits").contains("Websites")) continue

        val name = row.getByName("Common Name or Certificate Name")
        val cert = row.getByName("PEM Info")
        list.add("$name\n" + cert.substring(1, cert.length - 1))
    }
    pem.writeText(list.joinToString("\n\n"))
    File(pem.parent, pem.name + ".sha256sum").writeText(DigestUtil.sha256Hex(pem))
}
