package com.amazon.opendistroforelasticsearch.alerting.aggregation.bucketselectorext

import org.apache.lucene.document.Document
import org.apache.lucene.document.SortedNumericDocValuesField
import org.apache.lucene.document.SortedSetDocValuesField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.RandomIndexWriter
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.lucene.search.Query
import org.apache.lucene.util.BytesRef
import org.elasticsearch.common.CheckedConsumer
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.index.mapper.KeywordFieldMapper.KeywordFieldType
import org.elasticsearch.index.mapper.MappedFieldType
import org.elasticsearch.index.mapper.NumberFieldMapper
import org.elasticsearch.index.mapper.NumberFieldMapper.NumberFieldType
import org.elasticsearch.index.query.MatchAllQueryBuilder
import org.elasticsearch.script.MockScriptEngine
import org.elasticsearch.script.Script
import org.elasticsearch.script.ScriptEngine
import org.elasticsearch.script.ScriptModule
import org.elasticsearch.script.ScriptService
import org.elasticsearch.script.ScriptType
import org.elasticsearch.search.aggregations.Aggregation
import org.elasticsearch.search.aggregations.Aggregator
import org.elasticsearch.search.aggregations.AggregatorTestCase
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder
import org.elasticsearch.search.aggregations.bucket.filter.FiltersAggregationBuilder
import org.elasticsearch.search.aggregations.bucket.filter.InternalFilter
import org.elasticsearch.search.aggregations.bucket.filter.InternalFilters
import org.elasticsearch.search.aggregations.bucket.terms.IncludeExclude
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder
import org.elasticsearch.search.aggregations.metrics.AvgAggregationBuilder
import org.elasticsearch.search.aggregations.metrics.ValueCountAggregationBuilder
import org.hamcrest.CoreMatchers
import java.io.IOException
import java.util.Collections
import java.util.function.Consumer
import java.util.function.Function

class BucketSelectorExtAggregatorTestsIT : AggregatorTestCase() {

    private var SCRIPTNAME = "bucket_selector_script"
    private var paramName = "the_avg"
    private var paramValue = 19.0

    override fun getMockScriptService(): ScriptService {

        val scriptEngine = MockScriptEngine(
            MockScriptEngine.NAME,
            Collections.singletonMap(SCRIPTNAME,
                Function<Map<String?, Any?>, Any> { script: Map<String?, Any?> ->
                    script[paramName].toString().toDouble() == paramValue
                }), emptyMap()
        )
        val engines: Map<String, ScriptEngine> = Collections.singletonMap(scriptEngine.type, scriptEngine)
        return ScriptService(Settings.EMPTY, engines, ScriptModule.CORE_CONTEXTS)
    }

    @Throws(Exception::class)
    fun `test bucket selector script`() {
        val fieldType: MappedFieldType = NumberFieldType("number_field", NumberFieldMapper.NumberType.INTEGER)
        val fieldType1: MappedFieldType = KeywordFieldType("the_field")

        val filters: FiltersAggregationBuilder = FiltersAggregationBuilder("placeholder", MatchAllQueryBuilder())
            .subAggregation(
                TermsAggregationBuilder("the_terms").field("the_field")
                    .subAggregation(AvgAggregationBuilder("the_avg").field("number_field"))
            )
            .subAggregation(
                BucketSelectorExtAggregationBuilder(
                    "test_bucket_selector_ext",
                    Collections.singletonMap("the_avg", "the_avg.value"),
                    Script(ScriptType.INLINE, MockScriptEngine.NAME, SCRIPTNAME, emptyMap()),
                    "the_terms",
                    null
                )
            )
        paramName = "the_avg"
        paramValue = 19.0
        testCase(
            filters, MatchAllDocsQuery(),
            CheckedConsumer { iw: RandomIndexWriter ->
                var doc = Document()
                doc.add(SortedSetDocValuesField("the_field", BytesRef("test1")))
                doc.add(SortedNumericDocValuesField("number_field", 20))
                iw.addDocument(doc)
                doc = Document()
                doc.add(SortedSetDocValuesField("the_field", BytesRef("test2")))
                doc.add(SortedNumericDocValuesField("number_field", 19))
                iw.addDocument(doc)
            },
            Consumer { f: InternalFilters ->
                assertThat(
                    (f.buckets[0].aggregations
                        .get<Aggregation>("test_bucket_selector_ext") as BucketSelectorIndices).bucketIndices[0],
                    CoreMatchers.equalTo(1)
                )
            }, fieldType, fieldType1
        )
    }

    @Throws(Exception::class)
    fun `test bucket selector filter include`() {
        val fieldType: MappedFieldType = NumberFieldType("number_field", NumberFieldMapper.NumberType.INTEGER)
        val fieldType1: MappedFieldType = KeywordFieldType("the_field")

        val selectorAgg1: FiltersAggregationBuilder = FiltersAggregationBuilder("placeholder", MatchAllQueryBuilder())
            .subAggregation(
                TermsAggregationBuilder("the_terms").field("the_field")
                    .subAggregation(AvgAggregationBuilder("the_avg").field("number_field"))
            )
            .subAggregation(
                BucketSelectorExtAggregationBuilder(
                    "test_bucket_selector_ext",
                    Collections.singletonMap("the_avg", "the_avg.value"),
                    Script(ScriptType.INLINE, MockScriptEngine.NAME, SCRIPTNAME, emptyMap()),
                    "the_terms",
                    BucketSelectorExtFilter(IncludeExclude(arrayOf("test1"), arrayOf()))
                )
            )

        val selectorAgg2: FiltersAggregationBuilder = FiltersAggregationBuilder("placeholder", MatchAllQueryBuilder())
            .subAggregation(
                TermsAggregationBuilder("the_terms").field("the_field")
                    .subAggregation(AvgAggregationBuilder("the_avg").field("number_field"))
            )
            .subAggregation(
                BucketSelectorExtAggregationBuilder(
                    "test_bucket_selector_ext",
                    Collections.singletonMap("the_avg", "the_avg.value"),
                    Script(ScriptType.INLINE, MockScriptEngine.NAME, SCRIPTNAME, emptyMap()),
                    "the_terms",
                    BucketSelectorExtFilter(IncludeExclude(arrayOf("test2"), arrayOf()))
                )
            )

        paramName = "the_avg"
        paramValue = 19.0

        testCase(
            selectorAgg1, MatchAllDocsQuery(),
            CheckedConsumer { iw: RandomIndexWriter ->
                var doc = Document()
                doc.add(SortedSetDocValuesField("the_field", BytesRef("test1")))
                doc.add(SortedNumericDocValuesField("number_field", 20))
                iw.addDocument(doc)
                doc = Document()
                doc.add(SortedSetDocValuesField("the_field", BytesRef("test2")))
                doc.add(SortedNumericDocValuesField("number_field", 19))
                iw.addDocument(doc)
            },
            Consumer { f: InternalFilters ->
                assertThat(
                    (f.buckets[0].aggregations
                        .get<Aggregation>("test_bucket_selector_ext") as BucketSelectorIndices).bucketIndices.size,
                    CoreMatchers.equalTo(0)
                )
            }, fieldType, fieldType1
        )

        testCase(
            selectorAgg2, MatchAllDocsQuery(),
            CheckedConsumer { iw: RandomIndexWriter ->
                var doc = Document()
                doc.add(SortedSetDocValuesField("the_field", BytesRef("test1")))
                doc.add(SortedNumericDocValuesField("number_field", 20))
                iw.addDocument(doc)
                doc = Document()
                doc.add(SortedSetDocValuesField("the_field", BytesRef("test2")))
                doc.add(SortedNumericDocValuesField("number_field", 19))
                iw.addDocument(doc)
            },
            Consumer { f: InternalFilters ->
                assertThat(
                    (f.buckets[0].aggregations
                        .get<Aggregation>("test_bucket_selector_ext") as BucketSelectorIndices).bucketIndices[0],
                    CoreMatchers.equalTo(1)
                )
            }, fieldType, fieldType1
        )
    }

    @Throws(Exception::class)
    fun `test bucket selector filter exclude`() {
        val fieldType: MappedFieldType = NumberFieldType("number_field", NumberFieldMapper.NumberType.INTEGER)
        val fieldType1: MappedFieldType = KeywordFieldType("the_field")

        val selectorAgg1: FiltersAggregationBuilder = FiltersAggregationBuilder("placeholder", MatchAllQueryBuilder())
            .subAggregation(
                TermsAggregationBuilder("the_terms").field("the_field")
                    .subAggregation(AvgAggregationBuilder("the_avg").field("number_field"))
            )
            .subAggregation(
                BucketSelectorExtAggregationBuilder(
                    "test_bucket_selector_ext",
                    Collections.singletonMap("the_avg", "the_avg.value"),
                    Script(ScriptType.INLINE, MockScriptEngine.NAME, SCRIPTNAME, emptyMap()),
                    "the_terms",
                    BucketSelectorExtFilter(IncludeExclude(arrayOf(), arrayOf("test2")))
                )
            )
        paramName = "the_avg"
        paramValue = 19.0
        testCase(
            selectorAgg1, MatchAllDocsQuery(),
            CheckedConsumer { iw: RandomIndexWriter ->
                var doc = Document()
                doc.add(SortedSetDocValuesField("the_field", BytesRef("test1")))
                doc.add(SortedNumericDocValuesField("number_field", 20))
                iw.addDocument(doc)
                doc = Document()
                doc.add(SortedSetDocValuesField("the_field", BytesRef("test2")))
                doc.add(SortedNumericDocValuesField("number_field", 19))
                iw.addDocument(doc)
            },
            Consumer { f: InternalFilters ->
                assertThat(
                    (f.buckets[0].aggregations
                        .get<Aggregation>("test_bucket_selector_ext") as BucketSelectorIndices).bucketIndices.size,
                    CoreMatchers.equalTo(0)
                )
            }, fieldType, fieldType1
        )
    }

    @Throws(Exception::class)
    fun `test bucket selector filter numeric key`() {
        val fieldType: MappedFieldType = NumberFieldType("number_field", NumberFieldMapper.NumberType.INTEGER)
        val fieldType1: MappedFieldType = KeywordFieldType("the_field")

        val selectorAgg1: FiltersAggregationBuilder = FiltersAggregationBuilder("placeholder", MatchAllQueryBuilder())
            .subAggregation(
                TermsAggregationBuilder("number_agg").field("number_field")
                    .subAggregation(ValueCountAggregationBuilder("count").field("number_field"))
            )
            .subAggregation(
                BucketSelectorExtAggregationBuilder(
                    "test_bucket_selector_ext",
                    Collections.singletonMap("count", "count"),
                    Script(ScriptType.INLINE, MockScriptEngine.NAME, SCRIPTNAME, emptyMap()),
                    "number_agg",
                    BucketSelectorExtFilter(IncludeExclude(doubleArrayOf(19.0), doubleArrayOf()))
                )
            )

        paramName = "count"
        paramValue = 1.0
        testCase(
            selectorAgg1, MatchAllDocsQuery(),
            CheckedConsumer { iw: RandomIndexWriter ->
                var doc = Document()
                doc.add(SortedSetDocValuesField("the_field", BytesRef("test1")))
                doc.add(SortedNumericDocValuesField("number_field", 20))
                iw.addDocument(doc)
                doc = Document()
                doc.add(SortedSetDocValuesField("the_field", BytesRef("test2")))
                doc.add(SortedNumericDocValuesField("number_field", 19))
                iw.addDocument(doc)
            },
            Consumer { f: InternalFilters ->
                assertThat(
                    (f.buckets[0].aggregations
                        .get<Aggregation>("test_bucket_selector_ext") as BucketSelectorIndices).bucketIndices[0],
                    CoreMatchers.equalTo(0)
                )
            }, fieldType, fieldType1
        )
    }

    @Throws(Exception::class)
    fun `test bucket selector nested parent path`() {
        val fieldType: MappedFieldType = NumberFieldType("number_field", NumberFieldMapper.NumberType.INTEGER)
        val fieldType1: MappedFieldType = KeywordFieldType("the_field")

        val selectorAgg1: FilterAggregationBuilder = FilterAggregationBuilder("placeholder", MatchAllQueryBuilder())
            .subAggregation(
                FilterAggregationBuilder("parent_agg", MatchAllQueryBuilder())
                    .subAggregation(
                        TermsAggregationBuilder("term_agg").field("the_field")
                            .subAggregation(AvgAggregationBuilder("the_avg").field("number_field"))
                    )
            )
            .subAggregation(
                BucketSelectorExtAggregationBuilder(
                    "test_bucket_selector_ext",
                    Collections.singletonMap("the_avg", "the_avg.value"),
                    Script(ScriptType.INLINE, MockScriptEngine.NAME, SCRIPTNAME, emptyMap()),
                    "parent_agg>term_agg",
                    null
                )
            )
        paramName = "the_avg"
        paramValue = 19.0
        testCaseInternalFilter(
            selectorAgg1, MatchAllDocsQuery(),
            CheckedConsumer { iw: RandomIndexWriter ->
                var doc = Document()
                doc.add(SortedSetDocValuesField("the_field", BytesRef("test1")))

                doc.add(SortedNumericDocValuesField("number_field", 20))
                iw.addDocument(doc)
                doc = Document()
                doc.add(SortedSetDocValuesField("the_field", BytesRef("test2")))

                doc.add(SortedNumericDocValuesField("number_field", 19))
                iw.addDocument(doc)
            },
            Consumer { f: InternalFilter ->
                assertThat(
                    (f.aggregations
                        .get<Aggregation>("test_bucket_selector_ext") as BucketSelectorIndices).bucketIndices[0],
                    CoreMatchers.equalTo(1)
                )
            }, fieldType, fieldType1
        )
    }

    @Throws(IOException::class)
    private fun testCase(
        aggregationBuilder: FiltersAggregationBuilder, query: Query,
        buildIndex: CheckedConsumer<RandomIndexWriter, IOException>,
        verify: Consumer<InternalFilters>, vararg fieldType: MappedFieldType
    ) {
        newDirectory().use { directory ->
            val indexWriter = RandomIndexWriter(random(), directory)
            buildIndex.accept(indexWriter)
            indexWriter.close()
            DirectoryReader.open(directory).use { indexReader ->
                val indexSearcher = newIndexSearcher(indexReader)
                val filters: InternalFilters
                filters = searchAndReduce<InternalFilters, Aggregator>(indexSearcher, query, aggregationBuilder, *fieldType)
                verify.accept(filters)
            }
        }
    }

    @Throws(IOException::class)
    private fun testCaseInternalFilter(
        aggregationBuilder: FilterAggregationBuilder, query: Query,
        buildIndex: CheckedConsumer<RandomIndexWriter, IOException>,
        verify: Consumer<InternalFilter>, vararg fieldType: MappedFieldType
    ) {
        newDirectory().use { directory ->
            val indexWriter = RandomIndexWriter(random(), directory)
            buildIndex.accept(indexWriter)
            indexWriter.close()
            DirectoryReader.open(directory).use { indexReader ->
                val indexSearcher = newIndexSearcher(indexReader)
                val filters: InternalFilter
                filters = searchAndReduce<InternalFilter, Aggregator>(indexSearcher, query, aggregationBuilder, *fieldType)
                verify.accept(filters)
            }
        }
    }
}
