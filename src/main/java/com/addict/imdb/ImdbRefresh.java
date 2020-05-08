package com.addict.imdb;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.vfs2.FileSystemException;

import com.addict.common.Movie;
import com.addict.common.MovieDB;
import com.addict.utils.MovieDBSearch;

public class ImdbRefresh {

	public static void main(String[] args) throws Throwable {
		long start = System.currentTimeMillis();
		MovieDB movieDB = new MovieDB();
		
		if(args.length>0) {
			movieDB.downloadSize = Integer.parseInt(args[0]); 
		}
		
		System.out.println("Download Size:::" + movieDB.downloadSize);
		
		List<Movie> diskMovieList = ImdbRefresh.diskMoviesList(movieDB);
		searchDB(diskMovieList);
		
		ImdbSearch.readMovieList(diskMovieList, movieDB.startPage,movieDB);
		
		System.out.println("Done Fetching List in:::" + (System.currentTimeMillis() - start) / 1000L + " secs");
		
		Path path = Paths.get("failed.list", new String[0]);
		
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
		
		System.out.println("Done ImdbRefresh");
		System.exit(0);
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
			Files.write(Paths.get("download.list", new String[0]), "".getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
		}
		catch (Throwable e) {
			e.printStackTrace();
		}
		ImdbRefresh.getIgnoreList(movieDB);
		List<String> directoryList = ImdbRefresh.readDirList();
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
			movieDB.ignoreSet = Files.readAllLines(new File("ignore.list").toPath(), Charset.defaultCharset()).stream().collect(Collectors.toSet());
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
}

