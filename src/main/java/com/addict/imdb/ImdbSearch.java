package com.addict.imdb;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.addict.common.Movie;
import com.addict.common.MovieDB;
import com.addict.utils.MovieDBSearch;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class ImdbSearch {

	public static void readMovieList(List<Movie> diskMovieList, int startPage,MovieDB db) throws IOException {
		boolean fulfilled = false;
		int currentPageNo = startPage;
		Set<Movie> fileDownload = new HashSet<>();
		while (!fulfilled) {
			String currentUrl = db.startUrl;
			System.out.println(currentPageNo);
			currentUrl = db.startUrl.replace("{titleCount}", String.valueOf(currentPageNo));
			currentPageNo +=50;
			Set<Movie> retrieveList = getImdbMovieLink(currentUrl,db);
			if (!retrieveList.isEmpty()) {
				fileDownload.addAll(compareDatabase(retrieveList, diskMovieList,db));
			} else {
				fulfilled = true;
			}
			if (fileDownload.size() < db.downloadSize) 
				continue;
			fulfilled = true;
		}
		for(Movie x : fileDownload) {
			System.out.println("Working to Fetch::"+x.title);
			try {
				StringBuilder builder = new StringBuilder();
				builder.append(x.title);
				builder.append("|");
				x.imdb_link = parseImdbId(x.imdb_link);
				builder.append(x.imdb_link);
				String magnetLink = new ImdbSearch().findRarbg(x,db);
				builder.append("|");
				builder.append(magnetLink==null?"":magnetLink);
				builder.append("\n");
				Files.write(Paths.get("download.list", new String[0]), builder.toString().getBytes(), StandardOpenOption.APPEND);
			}
			catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}

	private static String parseImdbId(String imdb_link) {
		imdb_link = imdb_link.replace("/title/", "");
		imdb_link = imdb_link.replace("/?ref_=adv_li_tt", "");
		return imdb_link;
	}

	private String findRarbg(Movie movie,MovieDB db) throws Exception {

		String magnetLink = null;
		int retryCount =0;
		JsonElement elem = null;
		while(retryCount<5) {
			elem = fetchRarbgInfo(movie,db);
			if(!elem.toString().equals("[]"))
				break;
			retryCount++;
		}

		if(elem.toString().equals("[]"))
			return null;
		
		final JSONObject obj = new JSONObject("{"
				+ "movies:"
				+ ""+elem.toString()+
				"}");

		magnetLink = findRarbgLink(obj,"2160",movie,db); 

		if(magnetLink==null)
			magnetLink = findRarbgLink(obj,"1080",movie,db); 

		if(magnetLink==null)
			magnetLink = findRarbgLink(obj,"720",movie,db);

		return magnetLink;
	}


	private JsonElement fetchRarbgInfo(Movie movie, MovieDB db) {
		JsonElement elem = null;
		String rarbgUrl = "http://localhost:5000?imdbId="+movie.imdb_link;
		try {
			InputStream input = new URL(rarbgUrl).openStream();
			Reader reader = new InputStreamReader(input, "UTF-8");
			elem = JsonParser.parseReader(reader);
			
		}catch(Exception e) {
			e.printStackTrace();
		}
		return elem;
	}

	private String findRarbgLink(JSONObject obj, String resolution, Movie movie,MovieDB db) throws Exception { 

		if(obj.length()==0)
			return null;

		String magnetLink = null; 

		for(Object e : obj.getJSONArray("movies")) {

			JSONObject jsonElement = (JSONObject)e;

			if(finalMatch(resolution,obj,jsonElement,movie)) {
				magnetLink = jsonElement.getString("magnet");
				break;
			}
		}

		return magnetLink;
	}

	private boolean finalMatch(String resolution, JSONObject obj, JSONObject jsonElement, Movie movie) {
		if(resolution.equals("2160") && Float.parseFloat(movie.imdb_rating)<8) 
			return false;

		if(!checkSize(jsonElement,resolution))
			return false;

		if(resolution.equals("2160") && obj.toString().contains("HDR") && !jsonElement.getString("title").contains("HDR") && !jsonElement.getString("title").contains("10bit"))
			return false;

		if(!jsonElement.getString("title").contains(resolution))
			return false;

		return true;
	}

	private boolean checkSize(JSONObject e, String resolution) {
		float gb = Float.parseFloat(e.getString("size"))/(1024*1024*1024);

		if(resolution.equals("2160"))
			return true;
		else if(gb < 4.5F)
			return true;

		return false;
	}

	private static Set<Movie> compareDatabase(Set<Movie> retrieveList,
			List<Movie> diskMovieList,MovieDB db) throws IOException {
		Set<Movie> finalList = new HashSet<>();
		retrieveList.parallelStream().forEach(each-> {
			boolean found = false;
			List<String> key_id = null;
			try {
				key_id = MovieDBSearch.search(each.title);

			} catch (IOException e) {
				e.printStackTrace();
			}
			for (Movie curName : diskMovieList) {

				if(!key_id.contains(curName.id))
					continue;
				found = true;
				break;
			}
			if (!found && checkToIgnore(each.title,db)) {
				System.out.println("Found:" + each.title);
				each.id = String.valueOf(key_id);
				synchronized (ImdbSearch.class) {
					finalList.add(each);	
				}
			}
			System.out.println("Ignoring: " + each.title);
		});
		return finalList;
	}

	private static boolean checkToIgnore(String key,MovieDB db) {
		return !db.ignoreSet.contains(key);
	}

	private static Set<Movie> getImdbMovieLink(String currentUrl,MovieDB db) {
		Document doc = null;
		Set<Movie> retrieveList = new HashSet<>();
		try {
			boolean success = false;
			for (int i = 0; i < 10; ++i) {
				try {
					doc = Jsoup.connect((String)currentUrl).userAgent(db.userAgent).get();
					success = true;
					break;
				}
				catch (SocketTimeoutException ex) {
					ex.printStackTrace();
					System.err.println("Failed to retrieve imdbUrl :" + currentUrl);
					continue;
				}
				catch (IOException ex) {
					ex.printStackTrace();
				}
			}
			if (success) {
				Elements links = doc.select(".lister-item-header");
				Elements ratings  = doc.select(".col-imdb-rating");
				for(int j=0;j<links.size();j++) {
					Element childTitle = links.get(j).select("a[href]").first();
					String imdbLink = childTitle.attr("href");
					String movieName = childTitle.text();
					String rating = ratings.get(j).child(0).text();
					if (imdbLink != null) {
						Movie movie = new Movie();
						movie.title = movieName;
						movie.imdb_link = imdbLink;
						movie.imdb_rating = rating;
						retrieveList.add(movie);
					}
				}
			}
		}
		catch (Throwable e) {
			System.err.println("Failed to retrieve after multiple attempts :" + currentUrl);
		}
		return retrieveList;
	}
}
