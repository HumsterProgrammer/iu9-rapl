package AsmInt;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.util.NoSuchElementException;

import java.util.LinkedList;
import java.util.Hashtable;
import java.util.ArrayList;
import java.util.Vector;

class AsmInt{
	private int pos = 0;
	private int line = 0;
	
	private LinkedList<Token> tokenList = new LinkedList<Token>();
	private Hashtable<String, Integer> MetkaTable = new Hashtable<String, Integer>();
	
	private ArrayList<String> StopMetkaTable = new ArrayList<String>();
	public void addStopMetkaName(String name){
	    StopMetkaTable.add(name);
	}
	
	public void parse(InputStream is) throws ParseException{
		//parse program from inputstream
		Scanner scan = new Scanner(is);
		
		while(scan.hasNextLine()){
			parseLine(scan.nextLine());
			line++;
			pos=0;
		}
		
		int memoryPointer = MemorySpace.getStartAddress();
		int ignoreCount = 0;
		//change metka to address
		for(Token i: tokenList){
			if(i.ttype == TokenType.METKA){
				MetkaTable.put(i.str_attr(), memoryPointer);
				MemorySpace.addMetka(i.str_attr(),memoryPointer);
				ignoreCount++;
				i.addr = -1;
			}else if(i.ttype == TokenType.STOPMETKA){
			    if(StopMetkaTable.contains(i.str_attr())){
			        MemorySpace.addStopPointer(memoryPointer);
			    }
			    ignoreCount++;
			    i.addr = -1;
			}else{
				i.addr = memoryPointer++;
			}
		}
		/*for(Token i: tokenList){
			System.out.println(i);
		}*/
		
		int program_size = tokenList.size() - ignoreCount;
		MetkaTable.put("PROGRAM_SIZE", program_size+256);
		//System.out.println(MetkaTable);
		
		int[] program = new int[program_size];
		int index = 0;
		// translate to program
		for(Token i: tokenList){
			switch(i.ttype){
				case TokenType.NUM:
					program[index++] = parseNUM(i);
					break;
				case TokenType.KEYWORD:
					program[index++] = parseKEYWORD(i);
					break;
				case TokenType.IDENT:
					program[index++] = parseIDENT(i);
					break;
				default:
					break;
			}
		}
		//for(int i = 0; i< program.length; i++){
		//	System.out.println(program[i]);
		//}
		MemorySpace.loadProgram(program);
		System.out.println(String.format("Loaded %d words", program_size));
	}
	
	int parseNUM(Token t){
		String r = t.str_attr;
		boolean isNeg = false;
		int result = 0;
		int index = 0;
		if(r.charAt(0) == '+' || r.charAt(0) == '-'){
			index++;
			if(r.charAt(0) == '-'){
				isNeg = true;
			}
		}
		for(; index < r.length(); index++){
			result = result * 10 + (r.charAt(index) - '0');
		}
		if(isNeg) return -result;
		return result;
	}
	int parseKEYWORD(Token t){
		return MemorySpace.KEYWORDS.valueOf(t.str_attr()).code;
	}
	int parseIDENT(Token t) throws ParseException{
		try{
			return MetkaTable.get(t.str_attr());
		}catch(NullPointerException e){
			throw new ParseException(String.format("При обработке %s не найдена метка, возможно опечатка в ключевом слове", t));
		}
	}
	private void parseLine(String line) throws ParseException{
		//System.out.println(line);
		while(pos < line.length()){
			Token t = match(line.substring(pos));
			if(t.ttype() != TokenType.WS && t.ttype() != TokenType.COMMENT){
				tokenList.add(t);
			}
			pos += t.str_attr().length();
			if(t.ttype() == TokenType.METKA) pos++;
		}
	}
	Token match(String text) throws ParseException{
		Matcher result_match = null;
		TokenType result_tt = null;
			
		for (TokenType d : TokenType.values()) {
			Matcher m = d.matcher(text);
			if (m.find()) {
				if(result_tt == null || result_tt.domen < d.domen ||
						(result_tt.domen == d.domen &&
						result_match.group(0).length() < m.group(0).length())){
					result_match = m;
					result_tt = d;
				}
			}
		}
		if(result_match == null){
		    throw new ParseException(String.format("on (%d, %d) unrecognized pattern %s", line, pos, text));
		}
		//System.out.println(result_string);
		if(result_tt == TokenType.METKA){
			return new Token(result_tt, line+1, pos+1, result_match.group(1));
		}
		return new Token(result_tt, line+1, pos+1, result_match.group(0));
	}
	
	static void parseArg(AsmInt ai, String arg) throws ParseException{ // -param=value
	    String[] operands = arg.split("=");
	    //System.out.println(arg);
	    if(operands[0].equals("--help")){
	        System.out.println("AsmInt [аргументы] [имена asm файлов...]");
	        System.out.println("\tИнтерпретатор модельного ассемблера.\n\tПринимает последовательность входных файлов, транслирует их содержимое в виртуальное пространство и исполняет программу.");
	        System.out.println("\tЕсли не указаны файлы, принимает данные из последовательного ввода.");
	        System.out.println();
	        System.out.println("Аргументы");
	        System.out.println("\t--help                      вывод справки");
	        System.out.println("\t--mem=memsize               настройка размера памяти виртуальной машины. По умолчанию=1000 слов");
	        System.out.println("\t--debug=(fast|stepbystep)   запуск в режиме отладки: пишется в поток отладки информация об исполнении. По умолчанию пошаговом режиме");
	        System.out.println("\t--stopptr=addr              добавление точки остановки в отладке.");
	        System.out.println("\t          addr=number       соответствует адресу ячейки памяти");
	        System.out.println("\t          addr=\"<text>\"   соответствует метке <text> остановки в тексте программы");
	        System.out.println("\t--debugskip                 пропускает вывод дебага до достижения точки остановки");
	        
	        throw new ParseException("Справка");
	    }else if(operands[0].equals("--debug")){
	        MemorySpace.setDebug(true);
	        if(operands.length > 0){
	            if(operands[1].equals("fast")){
	                MemorySpace.setFast(true);
	            }
	        }
	    }else if(operands[0].equals("--stopptr")){
	        //stop pointer add
	        try{
	            MemorySpace.addStopPointer(Integer.parseInt(operands[1])); // addr
	        }catch(NumberFormatException e){
	            //STOP METKA <name>
	            ai.addStopMetkaName(operands[1]);
	        }
	    }else if(operands[0].equals("--debugskip")){
	        MemorySpace.setDebugSkip(true);
	    }else if(operands[0].equals("--mem")){
	        //set memory
	        try{
	            MemorySpace.MEMORY_SIZE = Integer.parseInt(operands[1]); // addr
	        }catch(NumberFormatException e){
	        }
	    }else{
	        throw new ParseException(String.format("arg %s not found", operands[0]));
	    }
	}

	public static void main(String[] args){
		AsmInt ai = new AsmInt();
		
		try{
			if(args.length == 0){
				ai.parse(System.in);
			}else{
			    Vector<FileInputStream> fislist = new Vector<FileInputStream>();
			    for(String arg : args){
			        if(arg.charAt(0) == '-'){ // params
			            parseArg(ai, arg);
			        }else{
			            try{
			                fislist.add(new FileInputStream(arg));
			            }catch(FileNotFoundException e){throw new ParseException(String.format("file %s not found", arg));}
			        }
			    }
			    MemorySpace.initMemorySpace();
			    InputStream is = new SequenceInputStream(fislist.elements());
				ai.parse(is);
			}
			
			System.out.println("\n"+MemorySpace.perform());
		}catch(ParseException e){System.out.println("global:"+e);}
	}
}

class Token{
	public int addr = -1;
	public int atribute = -1;
	
	TokenType ttype;
	int line;
	int pos;
	String str_attr;
	Token(TokenType ttype, int line, int pos, String str_attr){
		this.ttype=ttype;
		this.line=line;
		this.pos=pos;
		this.str_attr=str_attr;
	}
	TokenType ttype(){return this.ttype;}
	int line(){return this.line;}
	int pos(){return this.pos;}
	String str_attr(){return this.str_attr;}
	@Override
	public String toString(){
		return addr + " Token:[" +ttype + ","+ line + ","+pos+","+ str_attr +"]";
	}
}

enum TokenType{
	WS("^\\s+",0),
	COMMENT("^;[^\n\r]*$", 0),
	METKA("^:([_a-zA-Z](\\w|-)*)\\b",0),
	NUM("^[+\\-]?\\d+\\b",0),
	KEYWORD(MemorySpace.getOperationsPattern(),1),
	IDENT("^[_a-zA-Z](\\w|-)*\\b",0),
	STOPMETKA("^\\<[_a-zA-Z\\d]+?\\>", 0);
	
	
	public final Pattern pattern;
	public final int domen;
	
	//public abstract int parse(); 
	
	TokenType(String pattern, int domen){
		this.pattern = Pattern.compile(pattern);
		this.domen = domen;
	}
	public Pattern getPattern(){
		return this.pattern;
	}
	public Matcher matcher(String text){
		return this.pattern.matcher(text);
	}
	@Override
	public String toString(){
		return this.name();
	}
}
class ParseException extends Exception{
    ParseException(String msg){
        super(msg);
    }
}

