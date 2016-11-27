package io.thelandscape.krawler.crawler.Frontier

import com.github.andrewoma.kwery.core.Session
import com.github.andrewoma.kwery.mapper.*
import com.github.andrewoma.kwery.mapper.util.camelToLowerUnderscore
import io.thelandscape.krawler.http.KrawlUrl
import java.time.LocalDateTime

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

object krawlFrontierTable : Table<KrawlHistoryEntry, String>("krawlHistory",
        TableConfiguration(standardDefaults + timeDefaults,
                standardConverters + timeConverters, camelToLowerUnderscore)) {

    val Url by col(KrawlHistoryEntry::url, id = true)
    val Timestamp by col(KrawlHistoryEntry::timestamp)

    override fun idColumns(id: String) = setOf(Url of id)

    override fun create(value: Value<KrawlHistoryEntry>) = KrawlHistoryEntry(value of Url, value of Timestamp)

}

/**
 * KrawlFrontier HSQL Dao
 */
class KrawlFrontierHSQLDao(session: Session):
        KrawlFrontierIf, AbstractDao<KrawlHistoryEntry, String>(session, krawlFrontierTable, KrawlHistoryEntry::url) {

    override fun clearHistory(beforeTime: LocalDateTime): Int {
        val params = mapOf("timestamp" to beforeTime)
        val res = session.update("DELETE FROM ${table.name} WHERE timestamp = :timestamp", params)

        return res
    }

    override fun verifyUnique(url: KrawlUrl): Boolean {
        val params = mapOf("url" to url.normalForm)
        val res = session.select("SELECT COUNT(*) FROM ${table.name} WHERE url = :url",
                params, mapper = { it.resultSet.getLong(0) })

        return res.first() > 0
    }
}