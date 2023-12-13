package org.fao.geonet.kernel;

import com.fasterxml.jackson.databind.JsonNode;
import jeeves.server.context.ServiceContext;
import org.elasticsearch.action.search.SearchResponse;
import org.fao.geonet.AbstractCoreIntegrationTest;
import org.fao.geonet.domain.AbstractMetadata;
import org.fao.geonet.index.es.EsRestClient;
import org.fao.geonet.kernel.search.EsSearchManager;
import org.fao.geonet.kernel.setting.SettingManager;
import org.fao.geonet.kernel.setting.Settings;
import org.fao.geonet.utils.Xml;
import org.jdom.Element;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.Assert.*;

public class ElasticsearchIndexingTest extends AbstractIntegrationTestWithMockedSingletons {

    @Autowired
    private EsSearchManager searchManager;

    @Autowired
    private SettingManager settingManager;

    private ServiceContext serviceContext;

    @Before
    public void setUp() throws Exception {
        serviceContext = createServiceContext();
        settingManager.setValue(Settings.SYSTEM_XLINKRESOLVER_ENABLE, true);
        searchManager.init(true, Optional.of(List.of("records")));
    }

    @Test
    public void complexDatesAreIndexedCheck() throws Exception {
        // GIVEN
        AbstractMetadata dbInsertedSimpleDataMetadata = loadMetadataWithName("kernel/forest.xml");
        validateIndexedExpectedData(dbInsertedSimpleDataMetadata, "forest", 1);
        validateIndexedExpectedData(dbInsertedSimpleDataMetadata, "holocene", 0);
        URL dateResource = AbstractCoreIntegrationTest.class.getResource("kernel/holocene.xml");
        Element dateElement = Xml.loadStream(Objects.requireNonNull(dateResource).openStream());

        // WHEN
        AbstractMetadata dbInsertedMetadata = insertTemplateResourceInDb(serviceContext, dateElement);

        //THEN
        SearchResponse response = this.searchManager.query("_id:" + dbInsertedMetadata.getUuid() + " AND resourceTitleObject.default:holocene", null, 0, 10);
        long actualHitNbr = response.getHits().getTotalHits().value;
        assertEquals(String.format("Incorrect indexation of Holocene data with complex date due to: %s and %s", response, dbInsertedMetadata), 1, actualHitNbr);
        checkElasticsearchIndexStatus(2);
    }

    @Test
    public void ensureElasticsearchIndexIsTrulyEmpty() throws IOException {
        checkElasticsearchIndexStatus(0);
    }

    @Test
    public  void ensureResourceEditionIsNotInferredAsDate() throws Exception {
        AbstractMetadata dbInsertedSimpleDataMetadata = loadMetadataWithName("kernel/forest.xml");
    }

    private void checkElasticsearchIndexStatus(int expectedIndexationCount) throws IOException {
        EsRestClient esRestClient = searchManager.getClient();
        String defaultIndex = searchManager.getDefaultIndex();
        JsonNode indexStats = esRestClient.getIndexStats(defaultIndex);
        // https://www.elastic.co/guide/en/elasticsearch/reference/7.17/cluster-nodes-stats.html
        int count = indexStats.get("_all").get("primaries").get("indexing").get("index_total").asInt();
        assertEquals("Incorrect number of indexing operations requested on index " + defaultIndex, expectedIndexationCount, count);
    }

    private AbstractMetadata loadMetadataWithName(String name) throws Exception {
        URL dateResource = AbstractCoreIntegrationTest.class.getResource(name);
        Element dateElement = Xml.loadStream(Objects.requireNonNull(dateResource).openStream());
        return insertTemplateResourceInDb(serviceContext, dateElement);
    }

    private void validateIndexedExpectedData(AbstractMetadata dbInsertedSimpleDataMetadata, String resourceTitle, long expectedHitNbr) throws Exception {
        SearchResponse searchResponse = this.searchManager.query("resourceTitleObject.default:" + resourceTitle, null, 0, 10);
        long actualHitNbr = searchResponse.getHits().getTotalHits().value;
        String assertionErrorMessage = "The %s data was not indexed the expected number of times due to: %s and %s";
        assertEquals(String.format(assertionErrorMessage, resourceTitle, searchResponse, dbInsertedSimpleDataMetadata), expectedHitNbr, actualHitNbr);
    }
}
