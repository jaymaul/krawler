/**
 * Created by brian.a.madden@gmail.com on 11/24/16.
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

package io.thelandscape.krawler.crawler.History

import com.github.andrewoma.kwery.core.Session
import com.github.andrewoma.kwery.mapper.*
import com.github.andrewoma.kwery.mapper.util.camelToLowerUnderscore
import io.thelandscape.krawler.http.KrawlUrl
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.LocalDateTime

object krawlHistoryTable : Table<KrawlHistoryEntry, Long>("KrawlHistory",
        TableConfiguration(standardDefaults + timeDefaults,
                standardConverters + timeConverters, camelToLowerUnderscore)) {

    val Id by col(KrawlHistoryEntry::id, id = true)
    val Url by col(KrawlHistoryEntry::url)
    val RootPageId by col(KrawlHistoryEntry::rootPageId)
    val Timestamp by col(KrawlHistoryEntry::timestamp)

    override fun idColumns(id: Long) = setOf(Id of id)

    override fun create(value: Value<KrawlHistoryEntry>) =
            KrawlHistoryEntry(value of Id, value of Url, value of RootPageId, value of Timestamp)

}

/**
 * KrawlFrontier HSQL Dao
 */

class KrawlHistoryHSQLDao(session: Session):
        KrawlHistoryIf,
        AbstractDao<KrawlHistoryEntry, Long>(session,
                krawlHistoryTable, KrawlHistoryEntry::id, defaultIdStrategy = IdStrategy.Generated) {

    private val logger: Logger = LogManager.getLogger()

    init {
        // Create queue table
        session.update("CREATE TABLE IF NOT EXISTS KrawlHistory " +
                "(id INT IDENTITY, root_page_id INT, url VARCHAR(2048), timestamp TIMESTAMP)")
    }

    override fun insert(url: KrawlUrl, rootPageId: Int): KrawlHistoryEntry {
        val retVal = try {
            val entry = KrawlHistoryEntry(-1, url.canonicalForm, rootPageId, LocalDateTime.now())
            logger.debug("Inserting into history: " + entry)
            insert(entry)
        } catch (e: Throwable) {
            logger.error("There was an error inserting ${url.canonicalForm} to the KrawlHistory.")
            KrawlHistoryEntry()
        }

        return retVal
    }

    override fun clearHistory(beforeTime: LocalDateTime): Int {
        val convertedTs: String = beforeTime.toString().replace("T", " ")
        val params = mapOf("timestamp" to convertedTs)
        val res = session.update("DELETE FROM ${table.name} WHERE timestamp < :timestamp", params)

        return res
    }

    override fun clearHistoryByRootPageId(rootPageId: Int): Int {
        val params = mapOf("rootPageId" to rootPageId)
        val res = session.update("DELETE FROM ${table.name} WHERE root_page_id = :rootPageId", params)

        return res
    }

    override fun hasBeenSeen(url: KrawlUrl, rootPageId: Int): Boolean {
        val params = mapOf("url" to url.canonicalForm, "rootPageId" to rootPageId)
        val res = session.select("SELECT COUNT(*) FROM ${table.name} WHERE url = :url and root_page_id = :rootPageId",
                params, mapper = { it.resultSet.getLong(1) })

        return res.first() != 0L
    }
}
