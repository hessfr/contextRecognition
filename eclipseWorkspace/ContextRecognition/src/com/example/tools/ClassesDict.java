package com.example.tools;

import java.util.HashMap;
import java.util.Map;

/*
 * Singleton class holding the mapping from class names to class numbers.
 * To set the classesDict use: ClassesDict.getInstance().setMap(classesDict)
 * To get the classesDict use: ClassesDict.getInstance().getMap()
 */
public class ClassesDict {
	private Map<String, Integer> classesDict = new HashMap<String, Integer>();
	
	public Map<String, Integer> getMap() {
		return classesDict;
		
	}
	
	public String[] getStringArray() {
		
		int len = classesDict.size();
		
		String[] strArray = new String[len];
		
		int i=0;
		for ( String key : classesDict.keySet() ) {
			strArray[i] = key;
			i++;
		}
		
		return strArray;
		
	}
	
	public boolean isEmpty() {
		
		boolean isEmpty;
		
		int len = classesDict.size();
		
		if (len == 0) {
			isEmpty = true;
		} else {
			isEmpty = false;
		}
		
		return isEmpty;
		
	}
	
	public void setMap(Map<String, Integer> map) {
		
		this.classesDict = map;
	}
	
	
	private static final ClassesDict cd = new ClassesDict();
	
	
	public static ClassesDict getInstance() {
		return cd;
	}
}
