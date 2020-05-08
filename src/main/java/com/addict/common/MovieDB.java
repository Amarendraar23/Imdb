package com.addict.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class MovieDB {
	
	
	public MovieDB() {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream("moviedb.properties")) {

            Properties prop = new Properties();
            prop.load(input);
            
            this.downloadSize = Integer.parseInt(String.valueOf(prop.get("downloadSize")));
            this.startPage = Integer.parseInt(String.valueOf(prop.get("startPage")));
            this.startUrl = String.valueOf(prop.get("startUrl"));
            System.out.println(this.startUrl);

        } catch (IOException ex) {
            ex.printStackTrace();
        }
	}

	public String startUrl;
	public Set<String> ignoreSet = new HashSet<String>();
	public Map<String, String> failedList = new HashMap<String, String>();
	public int downloadSize = 100000;
	public int startPage = 1;
	public String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.122 Safari/537.36";
}
