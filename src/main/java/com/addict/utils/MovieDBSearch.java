package com.addict.utils;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MovieDBSearch {

	public static List<String> search(String movie_name) throws IOException {
		OkHttpClient client = new OkHttpClient();

		Request request = new Request.Builder()
		  .url("https://api.themoviedb.org/3/search/movie?"
		  		+ "include_adult=false&"
		  		+ "page=1&"
		  		+ "query="+movie_name +"&"
		  		+ "api_key=89c5fa15997c08d660f1e87ce94bcf02")
		  .get()
		  .build();

		Response response = client.newCall(request).execute();
		return getValuesForGivenKey(response.body().string(),"id");
	}

	public static List<String> getValuesForGivenKey(String jsonArrayStr, String key) {
		JSONObject jsonObject = new JSONObject(jsonArrayStr);
		JSONArray jsonArray = jsonObject.getJSONArray("results");
	    return IntStream.range(0, jsonArray.length())
	      .mapToObj(index -> ((JSONObject)jsonArray.get(index)).optString(key))
	      .collect(Collectors.toList());
	}
}
