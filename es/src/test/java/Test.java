import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

public class Test {

    @Autowired
    RestHighLevelClient restHighLevelClient;

    @org.junit.Test
    public void test() throws IOException {

        SearchResponse searchResponse = null;
        SearchRequest searchRequest = new SearchRequest("test-001");
        searchRequest.source();

        searchResponse = restHighLevelClient.search(searchRequest);
        searchResponse.getHits().getHits();
    }
}
