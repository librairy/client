package org.librairy.client.dao;

import com.google.common.collect.ImmutableMap;
import com.mashape.unirest.http.JsonNode;
import lombok.Setter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.librairy.client.exceptions.AlreadyExistException;
import org.librairy.client.exceptions.InvalidCredentialsError;
import org.librairy.client.model.DataItem;
import org.librairy.client.model.Item;
import org.librairy.client.model.Topic;
import org.librairy.client.model.Word;
import org.librairy.client.services.RestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
public class DomainsDao extends AbstractDao{

    private static final Logger LOG = LoggerFactory.getLogger(DomainsDao.class);

    @Setter
    private ItemsDao itemsDao;

    public DomainsDao(RestService api) {
        super(api);
    }

    public void updateTopics(String domain) throws IOException {
        api.put("/domains/"+domain+"/topics");
    }

    public String save(String name, String description) throws UnsupportedEncodingException, InvalidCredentialsError, AlreadyExistException {
        String domainId = URLEncoder.encode(name,"UTF-8");
        api.post("/domains/" + domainId, ImmutableMap.of("name", name, "description", description));
        return domainId;
    }

    public void addItem(String domainId, String itemId) throws InvalidCredentialsError, AlreadyExistException {
        api.post("/domains/"+domainId+"/items/"+itemId, ImmutableMap.of());
    }

    public List<Item> getItems(String domainId, Boolean content, Optional<Integer> size, Optional<String> offset) throws InvalidCredentialsError {

        StringBuilder path = new StringBuilder();

        path.append("/domains/"+domainId+"/items");

        if (size.isPresent()) path.append("?size=").append(size.get());

        if (offset.isPresent()) path.append("&offset=").append(offset.get());

        JsonNode result = api.get(path.toString());

        JSONArray list = result.getArray();
        List<Item> items = new ArrayList<>();
        for(int i=0;i<list.length();i++){
            JSONObject itemObject = list.getJSONObject(i);
            items.add(itemsDao.get(itemObject.getString("id"),content));
        }
        return items;
    }

    public boolean exists(String id){

        try {
            api.get("/domains/"+id);
            return true;
        } catch (InvalidCredentialsError e) {
            LOG.warn("Invalid credentials");
        }
        return false;
    }


    public List<Topic> getTopics(String domain1, Optional<Integer> numWords) throws InvalidCredentialsError {

        StringBuilder path = new StringBuilder();

        path.append("/domains/").append(domain1).append("/topics");

        if (numWords.isPresent()) path.append("?words=").append(numWords.get());

        return getTopics(path.toString());
    }

    public List<Topic> getTopics(String domain1, String itemId, Optional<Integer> numWords) throws InvalidCredentialsError {

        StringBuilder path = new StringBuilder();

        path.append("/domains/").append(domain1).append("/items/").append(itemId).append("/topics");

        if (numWords.isPresent()) path.append("?words=").append(numWords.get());

        return getTopics(path.toString());
    }

    private List<Topic> getTopics(String path) throws InvalidCredentialsError {

        JsonNode response = api.get(path);

        JSONArray topicsArray = response.getArray();

        List<Topic> topics = new ArrayList<>();

        for(int i=0; i<topicsArray.length(); i++){

            Topic topic = new Topic();

            JSONObject topicObject = topicsArray.getJSONObject(i);
            JSONObject topicRef = topicObject.getJSONObject("ref");

            topic.setId(topicRef.getString("id"));

            if (topicObject.has("score")){
                topic.setScore(topicObject.getDouble("score"));
            }

            JSONArray topicWords = topicObject.getJSONArray("words");
            for(int j=0; j< topicWords.length(); j++){

                JSONObject wordObject = topicWords.getJSONObject(j);
                JSONObject wordRef = wordObject.getJSONObject("ref");

                Word word = new Word();
                word.setValue(wordRef.getString("name"));
                word.setScore(wordObject.getDouble("value"));

                topic.add(word);

            }

            topics.add(topic);
        }

        return topics.stream().sorted((a,b) -> a.getId().compareTo(b.getId())).collect(Collectors.toList());
    }
}
