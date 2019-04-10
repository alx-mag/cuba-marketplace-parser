import com.fasterxml.jackson.databind.ObjectMapper
import org.jsoup.Jsoup
import org.jsoup.Jsoup.parse
import org.jsoup.nodes.Element
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat

const val rootUrl = "https://www.cuba-platform.com"
const val marketUrl = "$rootUrl/marketplace"
const val marketplaceTimeout = 10_000
const val componentPageTimeout = 5_000
const val targetCubaVersion = "7.0"
const val outputPath = "output.json"
val dateFormat = SimpleDateFormat("yyyy-MM-dd")

fun main() {
    parse()
}

fun parse() {
    val marketplace = parse(URL(marketUrl), marketplaceTimeout)
    val cells = marketplace.select(".views-row")
    val report = Report(cells.mapNotNull { parseCell(it) }.toList(), targetCubaVersion)
    println("Report is ready, ${report.appComponents.size} descriptors were created")
    val jsonFile = writeJSON(report)
    println("Output file: file:///${jsonFile.toAbsolutePath().toString().replace("\\", "/")}")
}

fun writeJSON(report: Report): Path {
    val file = Paths.get(outputPath)
    ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(file.toFile(), report)
    return file
}

fun parseCell(cellElement: Element): AppComponentDescriptor? {
    val versionRange = getVersion(cellElement) ?: return null
    if (versionRange.inRange(targetCubaVersion)) return null
    val description = (cellElement.selectFirst("div.views-field-body p") ?: cellElement.selectFirst("div.views-field-body"))?.text()
    val name = cellElement.selectFirst("div.views-field-title h3")?.text() ?: return null
    val rating = cellElement.selectFirst("div.star.star-1 span")?.text() ?: "0"

    val componentHref = cellElement.selectFirst("a.teaser-link")?.attr("href") ?: return null
    val id = componentHref.split("/").let { if (it.size == 3) it.component3() else return null }

    val page = Jsoup.parse(URL("$rootUrl$componentHref"), componentPageTimeout)
    val leftColumn = page.selectFirst("div.left-column")
    val coordinates = leftColumn.selectFirst("input.form-control")?.attr("value")?.split(":") ?: return null
    val vendor = leftColumn.selectFirst("div.field-name-field-addon-author-list div.field-item")?.text() ?: return null
    val category = leftColumn.selectFirst("div.field-name-field-addon-category a")?.text() ?: return null

    val dateLong = leftColumn.selectFirst("div.field-name-field-addon-updated span.date-display-single")?.text()?.let { dateFormat.parse(it).time }

    val tags =
        leftColumn.selectFirst("div.field-name-field-addon-tags")?.selectFirst("div.field-items")?.select("div.field-item a")
            ?.map { it.text() } ?: emptyList()

    return AppComponentDescriptor(id, name, description, category, tags, vendor, dateLong, rating, coordinates[0], coordinates[1], listOf(coordinates[2]))
}

fun getVersion(cellElement: Element): VersionRange? {
    val prefix = "Supported versions: "
    val versionsText = cellElement.select("span:contains($prefix)")?.text()
    val versions = versionsText?.substring(0, prefix.length)?.split("-") ?: return null
    val minVersion = versions[0]
    val maxVersion = if (versions.size == 2) versions.component2() else versions.component1()
    return VersionRange(minVersion, maxVersion)
}

data class VersionRange(val min: String, val max: String) {
    fun inRange(version: String): Boolean {
        return VersionComparatorUtil.compare(version, min) >= 0
                && VersionComparatorUtil.compare(version, max) <= 0
    }
}

data class AppComponentDescriptor(
    val id: String, val name: String, val description: String?, val category: String,
    val tags: List<String>, val vendor: String, val updateDateTime: Long?,
    val rating: String, val groupId: String, val artifactId: String, val versions: List<String>)

data class Report(val appComponents: List<AppComponentDescriptor>, val cubaVersion: String)

