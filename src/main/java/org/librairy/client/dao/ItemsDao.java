package org.librairy.client.dao;

import com.google.common.collect.ImmutableMap;
import com.mashape.unirest.http.JsonNode;
import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;
import org.librairy.client.exceptions.AlreadyExistException;
import org.librairy.client.exceptions.InvalidCredentialsError;
import org.librairy.client.model.Item;
import org.librairy.client.services.RestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Badenes Olmedo, Carlos <cbadenes@fi.upm.es>
 */
public class ItemsDao extends AbstractDao{

    private static final Logger LOG = LoggerFactory.getLogger(ItemsDao.class);

    public ItemsDao(RestService api) {
        super(api);
    }

    public String save(String name, String language, String content) throws InvalidCredentialsError, AlreadyExistException {
        JsonNode response = api.post("/items", ImmutableMap.of("name", name, "language", language, "content", content));
        return StringUtils.substringAfterLast(response.getObject().getString("uri"),"/");
    }

    public Item get(String id, Boolean content) throws InvalidCredentialsError {
        StringBuilder path = new StringBuilder();
        path.append("/items/").append(id);
        if (content) path.append("?content=true");
        JsonNode response       = api.get(path.toString());
        JSONObject itemObject   = response.getObject();
        JSONObject itemRef      = itemObject.getJSONObject("ref");
        Item item = new Item();

        item.setId(itemRef.getString("id"));
        item.setUri(itemRef.getString("uri"));
        item.setName(itemRef.getString("name"));
        item.setCreationTime(itemRef.getString("creation"));
        if (content) item.setContent(itemObject.getString("content"));
        item.setLanguage(itemObject.getString("language"));
        return item;
    }

    public String getAnnotation(String itemId, String annotationId) throws InvalidCredentialsError {
        JsonNode response               = api.get("/items/" + itemId+"/annotations/"+annotationId);
        JSONObject annotationObject     = response.getObject();
        JSONObject annotationValue      = annotationObject.getJSONObject("value");
        return annotationValue.getString("content");
    }
}
