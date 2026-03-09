package ca.digitalcave.moss.common;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This class makes Unix style command line parsing easy for Java applications.
 * Please see below for instructions on how to use it.
 *<p>
 * For a copy of the LGPL license, please see 
 *  http://www.gnu.org/copyleft/lesser.html
 *<p>
 * Copyright (C) 2005 by Wyatt Olson *<p> * This library is free software; you can redistribute it and/or * modify it under the terms of the GNU Lesser General Public * License as published by the Free Software Foundation; either * version 2.1 of the License, or (at your option) any later version. * <p> * This library is distributed in the hope that it will be useful, * but WITHOUT ANY WARRANTY; without even the implied warranty of * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU * Lesser General Public License for more details. * <p> * You should have received a copy of the GNU Lesser General Public * License along with this library; if not, write to the Free Software * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 * To use, simply create a collection of ParseVariable objects of
 * the correct types, and pass them to the static parse() method.
 * The return value of this is a ParseResults object, which you can
 * then extract the values from. 
 * 
 * @author wyatt
 */
public class ParseCommands {
	
	
	/**
	 * Parses command line arguments, and returns them as a mapping of flag (-x) to variable.  
	 * The input variables within flagVariables should be Objects (eg, Integer instead of int).
	 * Currently Boolean, Integer, String, Double , Character, and Float are supported; it is 
	 * simple to add more as required. 
	 * @param args Command line argument as given to the main method
	 * @param help The usage instructions which are shown if the command is type incorrectly.
	 * @param flags Collection of ParseVariables to define which flags to look for.
	 * @return
	 * @throws ParseException
	 */
	public static ParseResults parse(String[] args, String help, Collection<ParseVariable> flags) {
		ParseVariable[] flagArray = flags.toArray(new ParseVariable[1]);
		return parse(args, help, flagArray);
	}

	
	/**
	 * Parses command line arguments, and returns them as a mapping of flag (-x) to variable.  
	 * The input variables within flagVariables should be Objects (eg, Integer instead of int).
	 * Currently Boolean, Integer, String, Double , Character, and Float are supported; it is 
	 * simple to add more as required. 
	 * @param args Command line argument as given to the main method
	 * @param help The usage instructions which are shown if the command is type incorrectly.
	 * @param flags One or more ParseVariables to define which flags to look for.
	 * @return
	 * @throws ParseException
	 */
	public static ParseResults parse(String[] args, String help, ParseVariable... flags) {
		ParseResults results = new ParseResults(flags);
		
		//We loop through each string, looking for the __ replacement keyword.
		// This lets you put spaces in commands without making problems, in 
		// cases that the shell does not properly do this (appliction launchers, etc).
		for (int i = 0; i < args.length; i++) {
			args[i] = args[i].replaceAll("__", " ");

			//Check for Help flag 0 if found, print help and quit.
			if (args[i].equals("-h")
					|| args[i].equals("--help")){
				System.out.println(help);
				System.exit(0);
			}
		}
		
		
		for (int i = 0; i < args.length;){
			//If this is a flag, we try to parse it
			if (results.isFlag(args[i])){
				ParseVariable variable = results.get(args[i]);
				
				//Only boolean can be at the end of the list.
				if (variable.getType().equals(Boolean.class)){
					results.put(variable.getFlag(), true);
					i++;
				}
				else {
					if (i + 1 >= args.length
							|| results.isFlag(args[i + 1])){
						Logger.getLogger(ParseCommands.class.getName()).warning("Argument expected after flag " + args[i]);
						System.err.println(help);
						System.exit(1);
					}
					else {
						i++;
						try {
							if (variable.getType().equals(String.class)){
								results.put(variable.getFlag(), args[i]);
							}
							else if (variable.getType().equals(Integer.class)){
								results.put(variable.getFlag(), Integer.parseInt(args[i]));
							}
							else if (variable.getType().equals(Double.class)){
								results.put(variable.getFlag(), Double.parseDouble(args[i]));
							}
							else if (variable.getType().equals(Character.class)){
								results.put(variable.getFlag(), args[i].toCharArray()[0]);
							}
							else if (variable.getType().equals(Float.class)){
								results.put(variable.getFlag(), Float.parseFloat(args[i]));
							}
						}
						catch (NumberFormatException nfe){
							Logger.getLogger(ParseCommands.class.getName()).log(Level.WARNING, "Problem parsing number", nfe);
							results.put(variable.getFlag(), null);
						}
						
						i++;
					}
				}
			}
			//Otherwise, we assume it is a command.
			else {
				results.addCommand(args[i]);
				i++;
			}
		}
		
		
		//Check that all required flags are included:
		boolean requiredFlagsNotPresent = false;
		for (ParseVariable variable : results.getVariables()) {
			if (variable.isRequired() && results.get(variable.getFlag()).getValue() == null){
				requiredFlagsNotPresent = true;
				Logger.getLogger(ParseCommands.class.getName()).warning("Flag '" + variable.getFlag() + "' is required, but not present.");
			}
		}
		
		//If we are missing some required flags, exit.
		if (requiredFlagsNotPresent){
			System.err.println(help);
			System.exit(1);
		}
		
		return results;
	}
	
//	public static class ParseException extends Exception{
//		static final long serialVersionUID = 0;
//		
//		ParseException(Exception e){
//			super(e);
//		}
//		
//		ParseException(String s){
//			super(s);
//		}
//
//	}
	
	public static class ParseVariable implements Comparable<ParseVariable>{
		private final String flag;
		private Object value;
		private final Class<?> type;
		private final boolean required;
		
		public ParseVariable(String flag, Class<?> type, boolean required) {
			this.flag = flag;
			this.type = type;
			this.required = required;
		}
		
		public String getFlag() {
			return flag;
		}
		public boolean isRequired() {
			return required;
		}
		public void setValue(Object value){
			this.value = value;
		}
		public Object getValue() {
			return value;
		}
		public Class<?> getType(){
			return type;
		}
		public int compareTo(ParseVariable arg0) {
			return getFlag().compareTo(arg0.getFlag());
		}
		@Override
		public boolean equals(Object arg0) {
			if (arg0 instanceof ParseVariable)
				return this.compareTo((ParseVariable) arg0) == 0;
			else
				return false;
		}
	}
	
	public static class ParseResults {
		private final List<String> commands = new LinkedList<String>();;
		private final Map<String, ParseVariable> parseVariables = new HashMap<String, ParseVariable>();
		
		private ParseResults(ParseVariable... parseVariableList) {
			for (ParseVariable variable : parseVariableList) {
				parseVariables.put(variable.getFlag(), variable);
			}
		}
		
		private void addCommand(String command){
			commands.add(command);
		}
		
		private boolean isFlag(String flag){
			return parseVariables.get(flag) != null;
		}
		
		private void put(String flag, Object value){
			if (value.getClass().equals(parseVariables.get(flag).getType()))
				parseVariables.get(flag).setValue(value);
			else 
				System.out.println("ParseResults.put(): Not putting correct type");
		}
		
		private ParseVariable get(String flag){
			return parseVariables.get(flag);
		}
		
		public boolean getBoolean(String flag){
			if (parseVariables.get(flag).getType().equals(Boolean.class)){
				if (parseVariables.get(flag).getValue() != null)
					return (Boolean) parseVariables.get(flag).getValue();				
			}
		
			return false;
		}

		public String getString(String flag){
			if (parseVariables.get(flag).getType().equals(String.class))
				return (String) parseVariables.get(flag).getValue();
		
			return null;
		}

		public Double getDouble(String flag){
			if (parseVariables.get(flag).getType().equals(Double.class))
				return (Double) parseVariables.get(flag).getValue();
		
			return null;
		}

		public Float getFloat(String flag){
			if (parseVariables.get(flag).getType().equals(Float.class))
				return (Float) parseVariables.get(flag).getValue();
		
			return null;
		}

		public Character getCharacter(String flag){
			if (parseVariables.get(flag).getType().equals(Character.class))
				return (Character) parseVariables.get(flag).getValue();
		
			return null;
		}

		public Integer getInteger(String flag){
			if (parseVariables.get(flag).getType().equals(Integer.class))
				return (Integer) parseVariables.get(flag).getValue();
		
			return null;
		}
		
		private Collection<ParseVariable> getVariables(){
			return parseVariables.values();
		}
		
		public List<String> getCommands(){
			return commands;
		}
	}
	
	/**
	 * Simple method to find the index in an array which matches a specified string.
	 * @param str
	 * @param array
	 * @return
	 */
/*	private static int matchesArray(String str, Object[] array){
		for (int i = 0; i < array.length; i++){
			if (str.matches((String) array[i]))
				return i;
		}
		return -1;
	}
*/	
}
