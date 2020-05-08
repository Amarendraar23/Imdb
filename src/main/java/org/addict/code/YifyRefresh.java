package org.addict.code;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.vfs2.FileSystemException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.addict.common.Movie;
import com.addict.common.MovieDB;
import com.addict.utils.MovieDBSearch;

public class YifyRefresh {
	static String startUrl = "https://yts.am/browse-movies/0/all/all/7//rating{pageNo}";

	public static void main(String[] args) throws Throwable {
		long start = System.currentTimeMillis();
		int startPage = 0;
		MovieDB movieDB = new MovieDB();
		System.out.println("Download Size===" + movieDB.downloadSize);
		List<Movie> diskMovieList = YifyRefresh.diskMoviesList(movieDB);
		searchDB(diskMovieList);
		YifyRefresh.readMovieList(diskMovieList, startPage,movieDB);
		System.out.println("Done Fetching List in :" + (System.currentTimeMillis() - start) / 1000L + " secs");
		Path path = Paths.get("com/addict/code/failed.list", new String[0]);
		try (BufferedWriter writer = Files.newBufferedWriter(path, new OpenOption[0]);)
		{
			movieDB.failedList.keySet().forEach(x -> {
				try {
					writer.write(String.valueOf(x) + "\n");
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			});
		}
	}

	private static void searchDB(List<Movie> diskMovieList) throws IOException {
		diskMovieList.parallelStream().forEach(x->{
			try {
				List<String> idList = MovieDBSearch.search(x.title);
				if(!idList.isEmpty()) {
					x.id = idList.get(0);
					System.out.println(x.title+":::::"+x.id);
				}
				else
					System.err.println(x.title);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	private static ArrayList<Movie> diskMoviesList(MovieDB movieDB) throws FileSystemException {
		try {
			Files.write(Paths.get("com/addict/code/download.list", new String[0]), "".getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
		}
		catch (Throwable e) {
			e.printStackTrace();
		}
		YifyRefresh.getIgnoreList(movieDB);
		List<String> directoryList = YifyRefresh.readDirList();
		ArrayList<Movie> list = new ArrayList<Movie>();
		for (String dirString : directoryList) {
			File file = new File(dirString);

			String[] directories = file.list(new FilenameFilter(){

				@Override public boolean accept(File current, String name) { 
					return new File(current, name).isDirectory(); 
				} 
			});
			for(String s: directories) {
				list.add(stripYear(s));
			}
		}
		return list;
	}

	private static Movie stripYear(String s) {
		Movie m = new Movie();
		if(s.contains("(")) {
			m.title = s.substring(0, s.lastIndexOf("(")) ;
			try {
				int year = Integer.parseInt(s.substring(s.lastIndexOf("("),s.lastIndexOf("")));
				m.year = year;
			}catch(Exception e) {

			}
		}
		else
			m.title = s;

		return m;

	}

	private static void getIgnoreList(MovieDB movieDB) {
		try {
			movieDB.ignoreSet = Files.readAllLines(new File("com/addict/code/ignore.list").toPath(), Charset.defaultCharset()).stream().collect(Collectors.toSet());
		}
		catch (Throwable e) {
			e.printStackTrace();
		}
	}

	private static List<String> readDirList() {
		List<String> list = null;
		try {
			list = Files.readAllLines(new File("directory.list").toPath(), Charset.defaultCharset());
		}
		catch (Throwable e) {
			e.printStackTrace();
		}
		return list;
	}

	private static void readMovieList(List<Movie> diskMovieList, int startPage,MovieDB db) throws IOException {
		boolean fulfilled = false;
		int currentPageNo = startPage;
		HashMap<String, String> fileDownload = new HashMap<String, String>();
		while (!fulfilled) {
			
			String currentUrl = startUrl;
			System.out.println(currentPageNo);
			currentUrl = currentPageNo > 1 ? startUrl.replace("{pageNo}", "?page=" + String.valueOf(currentPageNo)) : startUrl.replace("{pageNo}", "");
			++currentPageNo;
			Map<String, String> retrieveList = YifyRefresh.getYifyMovieLink(currentUrl,db);
			
			if (!retrieveList.isEmpty()) {
				fileDownload.putAll(YifyRefresh.compareDatabase(retrieveList, diskMovieList,db));
			} else {
				fulfilled = true;
			}
			
			if (fileDownload.size() < db.downloadSize) {
				continue;
			}
				
			fulfilled = true;
		}
		fileDownload.entrySet().forEach(x -> {
			try {
				Files.write(Paths.get("com/addict/code/download.list", new String[0]), (String.valueOf((String)x.getKey()) + "\n" + (String)x.getValue() + "\n").getBytes(), StandardOpenOption.APPEND);
			}
			catch (Throwable e) {
				e.printStackTrace();
			}
		});
	}

	private static Map<String, String> compareDatabase(Map<String, String> retrieveList, List<Movie> diskMovieList,MovieDB db) throws IOException {
		HashMap<String, String> finalList = new HashMap<String, String>();
		for (Map.Entry<String, String> each : retrieveList.entrySet()) {
			boolean found = false;
			List<String> key_id = MovieDBSearch.search(each.getKey());
			for (Movie curName : diskMovieList) {
				//int ratio = FuzzySearch.ratio((String)curName, (String)each.getKey());
				//if (ratio <= 90) continue;
				//found = true;
				//break;
				
				if(!key_id.contains(curName.id))
					continue;
				found = true;
				break;
			}
			if (!found && YifyRefresh.checkToIgnore(each.getKey(),db)) {
				finalList.put(each.getKey(), each.getValue());
				System.out.println("Found:" + each.getKey());
				continue;
			}
			System.out.println("Ignoring: " + each.getKey());
		}
		return finalList;
	}

	private static boolean checkToIgnore(String key,MovieDB db) {
		return !db.ignoreSet.contains(key);
	}

	private static Map<String, String> getYifyMovieLink(String currentUrl,MovieDB db) {
		Document doc = null;
		HashMap<String, String> retrieveList = new HashMap<String, String>();
		try {
			boolean success = false;
			for (int i = 0; i < 10; ++i) {
				try {
					doc = Jsoup.connect((String)currentUrl).userAgent(db.userAgent).get();
					success = true;
					break;
				}
				catch (SocketTimeoutException ex) {
					System.err.println("Failed to retrieve for :" + currentUrl);
					continue;
				}
				catch (IOException ex) {
					// empty catch block
				}
			}
			if (success) {
				Elements links = doc.select("a.browse-movie-title");
				links.parallelStream().forEach(link -> {
					String yifyLink = link.attr("href");
					String movieName = link.text();
					String imdbLink = new YifyRefresh().getLink(yifyLink, "a.icon", "imdb", db);
					if (imdbLink != null) {
						int votesCount = new YifyRefresh().votesCount(imdbLink, db);
						if (votesCount >= 10000) {
							System.out.println(movieName);
							retrieveList.put(movieName, yifyLink);
						}
					} else {
						System.err.println("Failed to retrieve for :" + movieName);
						db.failedList.put(movieName+"("+imdbLink+")", yifyLink);
					}
				});
			}
		}
		catch (Throwable e) {
			System.err.println("Failed to retrieve for :" + currentUrl);
		}
		return retrieveList;
	}

	private int votesCount(String imdbLink,MovieDB db) {
		Document doc = null;
		try {
			doc = Jsoup.connect((String)imdbLink).userAgent(db.userAgent).get();
			Elements links = doc.select("span.small");
			for (Element link : links) {
				if (!link.attr("itemprop").equalsIgnoreCase("ratingCount")) continue;
				return Integer.parseInt(link.text().replaceAll(",", ""));
			}
		}
		catch (IOException e) {
			e.getLocalizedMessage();
		}
		return 0;
	}

	private String getLink(String yifyLink, String xPath, String xPected, MovieDB db) {
		Document doc = null;
		try {
			doc = Jsoup.connect((String)yifyLink).userAgent(db.userAgent).get();
			Elements links = doc.select(xPath);
			for (Element link : links) {
				String localLink = link.attr("href");
				if (!localLink.contains(xPected)) continue;
				return localLink;
			}
		}
		catch (IOException e) {
			e.getLocalizedMessage();
		}
		return null;
	}

}

