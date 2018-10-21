/**
 * Created by @brianmadden on 10/21/16.
 *
 * Copyright (c) <2016> <H, llc>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.thelandscape.krawler.http

import com.google.common.net.InternetDomainName
import org.jsoup.nodes.Element

/**
 * Class to represent a URL that was either provided as a seed, or found during crawl.
 * If the URL was found during crawl, it will contain additional information such as
 * the anchor text, rel property, and any other pertinent data, otherwise these fields
 * will be blank.
 *
 */

val InvalidKrawlUrl = KrawlUrl.new("", null)

class KrawlUrl private constructor(val rawUrl: String,
                                   val anchorText: String? = null,
                                   val anchorAttributes: Map<String, String>? = null,
                                   val wasExtractedFromAnchor: Boolean = false,
        // http://www.ietf.org/rfc/rfc2396.txt
        // URI syntax: [scheme:]scheme-specific-part[#fragment]
        // Absolute URLs start with a / immediately after the scheme portion
                                   val isAbsolute: Boolean = false,
                                   val scheme: String = "http",
        // Host is the part between the :// and the first /
                                   val host: String = "",
                                   val port: Int = 0,
                                   val path: String = rawUrl,
                                   val parent: KrawlUrl? = null) {

    companion object {

        fun new(url: String, parent: KrawlUrl? = null): KrawlUrl {
            return parse(url, parent)
        }

        fun new(anchor: Element, parent: KrawlUrl? = null): KrawlUrl {
            if (anchor.tagName() != "a" && !anchor.hasAttr("href"))
                return InvalidKrawlUrl
            return parse(anchor.attr("href"),
                    parent,
                    true,
                    anchor.text() ?: "",
                    anchor.attributes().associateBy({ it.key.toLowerCase() }, { it.value })
            )
        }

        private fun parse(url: String, parent: KrawlUrl?,
                          wasExtractedFromAnchor: Boolean = false,
                          anchorText: String? = null,
                          anchorAttributes: Map<String, String>? = null): KrawlUrl {
            var host = ""
            var port = 0
            var path = url
            var scheme = "http"
            var isAbsolute = false

            // Process the URL in one shot as best we can by treating it like a big state machine
            // This will handle the normalization process in-line to prevent excessive string mutations
            var idx = 0
            var hostStart = 0
            var nonHostSlashSeen = false
            var hostFound = false
            var firstColonFound = false

            // Used for decoding encoded characters
            val replaceList: List<Int> = listOf(
                    (0x41..0x5A),
                    (0x61..0x7A),
                    (0x30..0x39),
                    listOf(0x2D, 0x2E, 0x5F, 0x7E))
                    .flatten()

            // Used for blacklisting known bad schemes
            val blacklist: List<String> = listOf("mailto", "javascript", "tel", "file", "data", "irc", "ftp")

            while(idx < url.length) {
                val c = url[idx]

                if (c == ' ') {
                    return InvalidKrawlUrl
                }

                // Handle isAbsolute and finding the scheme
                if (c == ':') {
                    if (!nonHostSlashSeen && !firstColonFound) {
                        firstColonFound = true

                        // If the first colon was found the scheme is everything up until then
                        // NORMALIZE: Scheme should all be lowercase
                        val validator = Regex("[A-Za-z][\\w+-.]*")
                        val slice = url.slice(0 until idx).toLowerCase()
                        // NOTE: Right now we're ignoring any scheme that isn't http or https since we're only crawling
                        // websites. This isn't *technically* correct, but neither are a lot of URLs we find :-/
                        if (validator.matches(slice) && (slice == "http" || slice == "https"))
                            scheme = slice
                        else if (slice in blacklist)
                            return InvalidKrawlUrl
                        else
                            break


                        if (url.getOrNull(idx + 1) == '/' && url.getOrNull(idx + 2) == '/') {
                            isAbsolute = true

                            // If we have a string of / leave only 2
                            var n: Int = 3
                            while ((idx + n) < url.length && url[idx + n] == '/') n++
                            // Move idx by n to the start of the host portion
                            idx += n
                            // Start the host after the scheme
                            hostStart = idx
                            continue
                        }
                    }

                    // Find the port if it's present
                    if (!nonHostSlashSeen && !hostFound && firstColonFound) {
                        // This should be the port
                        var portIdxAdder = 1
                        // Get all of the digits after the colon
                        while (url.getOrElse(idx + portIdxAdder, { ' ' }).isDigit()) {
                            portIdxAdder++
                        }

                        val slice = url.slice(idx + 1 until idx + portIdxAdder)
                        port = if (slice.isNotBlank()) slice.toInt() else port

                        // Increment the index and move on
                        idx += portIdxAdder
                        continue
                    }
                }

                // Everything up until the port is the host portion
                if (c == '/') {
                    // Handle the case where scheme is left out to be inferred from the parent
                    if (idx == 1 && url[0] == '/' && parent != null) {
                        scheme = parent.scheme
                        isAbsolute = true
                    }

                    if (isAbsolute) {
                        // NORMALIZATION: convert to lowercase for normalization
                        // NORMALIZATION: Remove the port if the scheme is http
                        host = url.slice(hostStart until idx)
                                .toLowerCase()
                                .replace(Regex(":[0-9]+"), "")

                        hostFound = true

                        path = url.slice(idx until url.length)

                        // If we've come this far we're ready to process the path
                        break
                    }
                    nonHostSlashSeen = true
                }

                // Increment idx if we didn't do any special handling
                idx++

                // End the initial processing. From here we'll only process the path.
                // This will also be treated as a state machine for single pass processing
            }

            // If no host was found but this is an absolute URL then the host is everything after the scheme
            if (!hostFound && isAbsolute) {
                host = url.slice(hostStart until url.length)
                path = "/"
            } else if (!hostFound && !isAbsolute) {
                // If there host was not found and it is not an absolute URL the host comes from the parent
                // and the entire URL is just a path
                host = parent?.host ?: ""
                // Add a leading slash if there wasn't one
                path = if (path.startsWith("/")) path else "/" + path
            }

            // Reset idx
            idx = 0
            while(idx < path.length) {
                val c = path[idx]

                // Handle normalization of path from here on out
                if (c == ' ') {
                    path = path.slice(0 until idx) + "%20" + path.slice(idx + 1 until path.length)
                    idx += 3
                    continue
                }

                if (c == '|') {
                    path = path.slice(0 until idx) + "%7C" + path.slice(idx + 1 until path.length)
                    idx += 3
                    continue
                }

                if (c == '\n') {
                    path = path.slice(0 until idx) + path.slice(idx + 1 until path.length)
                    continue
                }

                if (c == '%') {
                    val nextTwoChars: String = if (idx + 1  >= path.length) {
                        idx++
                        continue
                    } else if (idx + 2 >= path.length) {
                        idx += 2
                        continue
                    } else {
                        path.slice(idx + 1..idx + 2)
                    }

                    if (nextTwoChars.matches(Regex("[0-9a-fA-F]{2}"))) {
                        val nextTwo: Int = Integer.parseInt(nextTwoChars, 16)

                        // NORMALIZATION:
                        // We've hit an encoded character, decide whether or not to decode (non-reserved chars)
                        // Unreserved characters are %41-%5A, %61-7A, %30-%39, %2D, %2E, %5F, %7E
                        if (nextTwo in replaceList) {
                            if (idx + 3 < path.length)
                                path = path.slice(0 until idx) + nextTwo.toChar() + path.slice(idx + 3 until path.length)
                            else
                                path = path.slice(0 until idx) + nextTwo.toChar()
                            // Only increment by 1 since we replaced 3 characters with 1
                            idx++
                            continue
                        }
                    }

                    // NORMALIZATION: If they weren't converted make sure they're upper case
                    path = if (idx + 3 < url.length)
                        path.slice(0 .. idx) + nextTwoChars.toUpperCase() + path.slice(idx + 3 until path.length)
                    else
                        path.slice(0 .. idx) + nextTwoChars.toUpperCase()

                    idx += 3
                    continue
                }

                if (c == '/') {
                    // TODO: Remove excess slashes after a slash

                    // NORMALIZATION: Remove any /../ or /./
                    val nextTwo = "" + path.getOrElse(idx + 1, {' '}) + path.getOrElse(idx + 2, {' '})

                    if (nextTwo == "./") {
                        path = path.slice(0 until idx) + path.slice(idx + 2 until path.length)

                        continue
                    }

                    if (nextTwo == "..") {
                        if (idx + 3 < path.length)
                            path = path.slice(0 until idx) + path.slice(idx + 3 until path.length)
                        else
                            path = path.slice(0 until idx)

                        continue
                    }
                }

                // If we didn't do anything, just increment the index
                idx++
            }

            // Handle URLs that didn't have a port at all
            if (port == 0) {
                if ("http" == scheme) port = 80
                if ("https" == scheme) port = 443
            }

            return KrawlUrl(
                    url,
                    anchorText,
                    anchorAttributes,
                    wasExtractedFromAnchor,
                    isAbsolute,
                    scheme,
                    host,
                    port,
                    path,
                    parent
            )
        }
    }

    val normalForm: String = if (host + path == "") "" else "$scheme://$host$path"
    // The hierarchical part is everything up to the path
    val hierarchicalPart: String ="$scheme://$host"

    // Get a list of TLDs from https://publicsuffix.org/list/public_suffix_list.dat
    // These are all lazy because the suffix finder is a bit of an expensive operation that isn't always necessary.
    private val idn: InternetDomainName? by lazy {
        try {
            InternetDomainName.from(this.host)
        } catch (e: Throwable) {
            null
        }
    }

    val suffix: String by lazy { (idn?.publicSuffix() ?: "").toString() }
    val domain: String by lazy { host.replace("." + suffix, "").split(".").last() + "." + suffix }
    val subdomain: String by lazy { host.replace("." + domain, "") }

    // Create a canonical form that we can use to better determine if a URL has been seen before
    // TODO: Decide to add or remove / at end of URL -- this may change semantics on some web servers
    // if (normalForm.endsWith(suffix)) normalForm + "/" else normalForm
    // TODO: Remove ? if no query parameters follow
    // TODO: Remove duplicate / after scheme portion
    val canonicalForm: String
        get() = if (anchorAttributes != null &&
                anchorAttributes.contains("rel") &&
                anchorAttributes["rel"] == "canonical") rawUrl else normalForm

    override fun toString(): String = canonicalForm

    override fun equals(other: Any?): Boolean {
        if (other == null)
            return false

        if (other !is KrawlUrl)
            return false

        return this.canonicalForm == other.canonicalForm
    }
}
