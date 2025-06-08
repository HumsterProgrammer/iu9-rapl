% Лабораторная работа № 1 «Стековая виртуальная машина»
% 25 марта 2025 г.
% Денис Кочетков, ИУ9-61Б

# Цель работы

Целью данной работы является написание интерпретатора ассемблера модельного компьютера, который
 будет использоваться в последующих лабораторных как целевой язык.

# Индивидуальный вариант

Нужно написать на модельном ассемблере программу и запустить её.

Программа считывает с устройства ввода десятичное число и распечатывает на устройство вывода число в
 шестнадцатеричной системе счисления.

# Реализация интерпретатора

Файл `AsmInt.java`
```java
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.util.NoSuchElementException;

import java.util.LinkedList;
import java.util.Hashtable;

class AsmInt{
	private int pos = 0;
	private int line = 0;
	
	private LinkedList<Token> tokenList = new LinkedList<Token>();
	private Hashtable<String, Integer> MetkaTable = new Hashtable<String, Integer>();
	
	public void parse(String source, InputStream is) throws Exception{
		//parse program from inputstream
		Scanner scan = new Scanner(is);
		
		while(scan.hasNextLine()){
			parseLine(scan.nextLine());
			line++;
			pos=0;
		}
		
		int memoryPointer = MemorySpace.getStartAddress();
		//change metka to address
		for(Token i: tokenList){
			if(i.ttype == TokenType.METKA){
				MetkaTable.put(i.str_attr(), memoryPointer);
				i.addr = -1;
			}else{
				i.addr = memoryPointer++;
			}
		}
		
		int[] program = new int[tokenList.size() - MetkaTable.size()];
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

		MemorySpace.loadProgram(program);
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
	int parseIDENT(Token t) throws Exception{
		try{
			return MetkaTable.get(t.str_attr());
		}catch(NullPointerException e){
			throw new Exception(String.format(
			"При обработке %s не найдена метка, возможно опечатка в ключевом слове", t));
		}
	}
	private void parseLine(String line){
		while(pos < line.length()){
			Token t = match(line.substring(pos));
			if(t.ttype() != TokenType.WS && t.ttype() != TokenType.COMMENT){
				tokenList.add(t);
			}
			pos += t.str_attr().length();
			if(t.ttype() == TokenType.METKA) pos++;
		}
	}
	Token match(String text){
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
		//System.out.println(result_string);
		if(result_tt == TokenType.METKA){
			return new Token(result_tt, line+1, pos+1, result_match.group(1));
		}
		return new Token(result_tt, line+1, pos+1, result_match.group(0));
	}	

	public static void main(String[] args){
		AsmInt ai = new AsmInt();
		
		try{
			if(args.length == 0){
				ai.parse("input", System.in);
			}else{
				for(String file : args){
					ai.parse(file, new FileInputStream(file));
				}
			}
			
			System.out.println("\n"+MemorySpace.perform());
		}catch(Exception e){System.out.println(e);}
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
		return "Token:[" +ttype + ","+ line + ","+pos+","+ str_attr +"]";
	}
}

enum TokenType{
	WS("^\\s+",0),
	COMMENT("^;[^\n\r]*$", 0),
	METKA("^:([_a-zA-Z](\\w|-)*)\\b",0),
	NUM("^[+\\-]?\\d+\\b",0),
	KEYWORD(MemorySpace.getOperationsPattern(),1),
	IDENT("^[_a-zA-Z](\\w|-)*\\b",0);
	
	
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
```

Файл `MemorySpace.java`
```java
import java.util.TreeMap;
import java.util.Iterator;
import java.util.Arrays;
import java.util.Scanner;

import java.io.IOException;

class MemorySpace{
	public static final int PROGRAM_SIZE = 1000;
	
	private static final int[] memory = new int[PROGRAM_SIZE];
	private static int end_of_program = 256;
	
	private static int IP;
	private static int SP;
	private static int FP;
	private static int RV;
	
	private static int END = Integer.MIN_VALUE; // код возврата
	
	private static TreeMap<Integer, KEYWORDS> OperatorsMap = KEYWORDS.MakeKeywordMap();
	
	public static void loadProgram(int[] program){
		for(int i = 0; i < program.length; i++){
			memory[i + end_of_program] = program[i];
		}
		end_of_program += program.length;
	}
	public static int getStartAddress(){
		return end_of_program;
	}
	
	public static int perform(){
		SP = PROGRAM_SIZE;
		FP = Integer.MIN_VALUE;
		RV = Integer.MIN_VALUE;
		
		for(IP=256;END == Integer.MIN_VALUE; ){
			// Вывод лога исполнения в stderr
			/*System.err.printf("%d: ", IP);
			if(memory[IP] > 0){
				System.err.print(memory[IP]);
			}else{
				System.err.print(OperatorsMap.get(memory[IP]));
			}
			System.err.printf("\n%d,%d,%d\n", SP, FP,RV);
			for(int i = SP; i < PROGRAM_SIZE; i++){
				System.err.print(memory[i] + " ");
			}
			System.err.println();*/
			
			if(memory[IP] < 0){
				int code = memory[IP++];
				OperatorsMap.get(code).action();
			}else{
				push(memory[IP++]);
			}
		}
		return END;
	}
	public static int pop(){
		return memory[SP++];
	}
	public static void push(int x){
		memory[--SP] = x;
	}
	public static int get(int addr){
		return memory[addr];	
	}
	public static void set(int addr, int value){
		memory[addr]=value;
	}
	public static int returnCode(){
		return END;
	}
	
	public static void main(String[] args){
		// Тестирование работы класса на вручную заданном примере
		MemorySpace.set(256, '0');
		MemorySpace.set(257, 1);
		MemorySpace.set(258, KEYWORDS.ADD.code);
		MemorySpace.set(259, KEYWORDS.OUT.code);
		MemorySpace.set(260, '\n');
		MemorySpace.set(261, KEYWORDS.OUT.code);
		
		MemorySpace.set(262, 1);
		MemorySpace.set(263, KEYWORDS.HALT.code);
		
		System.out.println(MemorySpace.perform());
	}
	
	public static String getOperationsPattern(){
		return KEYWORDS.getPatternString();
	}
	
	//########################Реализация#команд##################################
	public enum KEYWORDS{
		ADD(-1){
			public void action(){
				int y = pop();
				int x = pop();
				push(x+y);
			}
		},
		SUB(-2){
			public void action(){
				int y = pop();
				int x = pop();
				push(x-y);
			}
		},
		MUL(-40){
			public void action(){
				int y = pop();
				int x = pop();
				push(x*y);
			}
		},
		DIV(-41){
			public void action(){
				int y = pop();
				int x = pop();
				push(x / y);
			}
		},
		MOD(-42){
			public void action(){
				int y = pop();
				int x = pop();
				push(x % y);
			}
		},
		NEG(-33){
			public void action(){
				int x = pop();
				push(-x);
			}
		},
		BITAND(-3){
			public void action(){
				int y = pop();
				int x = pop();
				push(x & y);
			}
		},
		BITOR(-4){
			public void action(){
				int y = pop();
				int x = pop();
				push(x | y);
			}
		},
		BITXOR(-5){
			public void action(){
				int y = pop();
				int x = pop();
				push(x ^ y);
			}
		},
		BITNOT(-34){
			public void action(){
				int x = pop();
				push(~x);
			}
		},
		LSHIFT(-6){
			public void action(){
				int y = pop();
				int x = pop();
				push(x << y);
			}
		},
		RSHIFT(-7){
			public void action(){
				int y = pop();
				int x = pop();
				push(x >> y);
			}
		},
		DUP(-25){
			public void action(){
				int x = pop();
				push(x);
				push(x);
			}
		},
		DROP(-26){
			public void action(){
				pop();
			}
		},
		SWAP(-27){
			public void action(){
				int y = pop();
				int x = pop();
				push(y);
				push(x);
			}
		},
		ROT(-28){
			public void action(){
				int z = pop();
				int y = pop();
				int x = pop();
				push(y);
				push(z);
				push(x);
			}
		},
		OVER(-29){
			public void action(){
				int y = pop();
				int x = pop();
				push(x);
				push(y);
				push(x);
			}
		},
		SDROP(-30){
			public void action(){
				int y = pop();
				int x = pop();
				push(y);
			}
		},
		DROP2(-24){
			public void action(){
				pop();
				pop();
			}
		},
		LOAD(-35){
			public void action(){
				int a = pop();
				push(get(a));
			}
		},
		SAVE(-36){
			public void action(){
				int v = pop();
				int a = pop();
				set(a, v);
			}
		},
		GETIP(-9){
			public void action(){
				push(IP);
			}
		},
		GETSP(-10){
			public void action(){
				push(SP);
			}
		},
		GETFP(-11){
			public void action(){
				push(FP);
			}
		},
		GETRV(-12){
			public void action(){
				push(RV);
			}
		},
		SETIP(-13){
			public void action(){
				IP = pop();
			}
		},
		SETSP(-14){
			public void action(){
				SP = pop();
			}
		},
		SETFP(-15){
			public void action(){
				FP = pop();
			}
		},
		SETRV(-16){
			public void action(){
				RV = pop();
			}
		},
		CMP(-8){
			public void action(){
				int y = pop();
				int x = pop();
				if(x < y){
					push(-1);
				}else if(x == y){
					push(0);
				}else{
					push(1);
				}
			}
		},
		JMP(-13){
			public void action(){
				IP = pop();
			}
		},
		JLT(-23){
			public void action(){
				int a = pop();
				int x = pop();
				if(x < 0){
					IP = a;
				}
			}
		},
		JGT(-20){
			public void action(){
				int a = pop();
				int x = pop();
				if(x > 0){
					IP = a;
				}
			}
		},
		JEQ(-22){
			public void action(){
				int a = pop();
				int x = pop();
				if(x == 0){
					IP = a;
				}
			}
		},
		JLE(-21){
			public void action(){
				int a = pop();
				int x = pop();
				if(x <= 0){
					IP = a;
				}
			}
		},
		JGE(-18){
			public void action(){
				int a = pop();
				int x = pop();
				if(x >= 0){
					IP = a;
				}
			}
		},
		JNE(-19){
			public void action(){
				int a = pop();
				int x = pop();
				if(x != 0){
					IP = a;
				}
			}
		},
		CALL(-31){
			public void action(){
				int a = pop();
				push(IP);
				IP = a;
			}
		},
		RET(-13){
			public void action(){
				int a = pop();
				IP = a;
			}
		},
		RET2(-17){
			public void action(){
				int a = pop();
				pop();
				IP = a;
			}
		},
		IN(-43){
			public void action(){
				try{
					push(System.in.read());
				}catch(IOException e){
					System.err.println("ioexception while read");
					END=444;
				}
			}
		},
		OUT(-44){
			public void action(){
				System.out.write(pop());
			}
		},
		HALT(-37){
			public void action(){
				END = pop();
			}
		};
		
		public final int code;
		public abstract void action();
		
		KEYWORDS(int x){
			this.code = x;
		}
		@Override
		public String toString(){return this.name();}
		
		//#########################################################################
		
		public static TreeMap<Integer, KEYWORDS> MakeKeywordMap(){
			TreeMap<Integer, KEYWORDS> resultMap = new TreeMap<Integer, KEYWORDS>();
			for(KEYWORDS i: KEYWORDS.values()){
				resultMap.put(i.code, i);
			}
			return resultMap;
		}
		public static String getPatternString(){
			String result = "^(";
			Iterator<KEYWORDS> iter = Arrays.asList(KEYWORDS.values()).iterator();
			
			result+=iter.next().toString();
			while(iter.hasNext()){
				result += "|"+iter.next().toString();
			}
			return result+")\\b";
		}
	}
}
```

# Программа на ассемблере

```
; in dec
; out hex

; return codes 
; 0 - ok
; 1 - invalid input

BUFFER_POINTER SETFP 
;##############input###############
:INPUT_FOR
	IN DUP GETFP SWAP SAVE ; читает символ, сохраняет в буфере и оставляет на стеке его же
	INCREMENT_FP CALL
	10 CMP INPUT_FOR JNE ; если не конец строки, повторить цикл иначе идти дальше

;теперь в буфере лежит число 
;##########DEC#TO#INT##############
BUFFER_POINTER SETFP
0 SETRV
:BEGIN_OF_COMPUTE
	GETFP LOAD 10 CMP END_OF_COMPUTE JEQ ; если мы достигли конца строки, то выходим из цикла
	GETFP LOAD ; читаем символ
	48 SUB ; вычитаем код 0
	GETRV 10 MUL ; получаем значение в ячейке и домножаем его на 10 
	ADD SETRV ; добавляем и запоминаем значение
	INCREMENT_FP CALL
	BEGIN_OF_COMPUTE JMP
:END_OF_COMPUTE


; в RV лежит считанное число
;#############INT#TO#HEX##########
99 ; ставим на дно стека метку конца вывода (99)
:BEGIN_TO_HEX
	GETRV 16 MOD
	GETRV 16 DIV SETRV
	
	GETRV BEGIN_TO_HEX JNE  ; если в регистре 0, то выходим. BEGIN_TO_HEX JMP
:END_TO_HEX

;#############OUT#################
:OUT_FOR
	DUP 99 CMP END_OUT JEQ ; если достигли конца, то прыгаем на конец вывода
	DUP 10 CMP OUT_ELSE JLT ; если значение меньше 10 то прибавляем код 0, иначе код A с вычетом 10
		55 ADD OUT ; x-10+'A' = x + 55
		OUT_ENDIF JMP
	:OUT_ELSE
		48 ADD OUT ; x + '0'
	:OUT_ENDIF
	
	OUT_FOR JMP
:END_OUT
0 HALT

;#############FUNCTIONS###########
:INCREMENT_FP ;инкремент указателя кадра
	GETFP 1 ADD SETFP RET 
	; здесь БЫЛ баг, что call возвращает на ту же команду,
	; с которой перешел, соответственно снова выполняется call

;##############ERRORS#############
:INVALID_INPUT
	1 HALT

;##############MEMORY#############
:BUFFER_POINTER
```

# Тестирование

Результаты запуска тестовой программы на разных входных данных:

```
vudrav@vudrav-XL442:~/Рабочий стол/rayp1$ java AsmInt laba.asm 2> log
255
FF
0
vudrav@vudrav-XL442:~/Рабочий стол/rayp1$ java AsmInt laba.asm 2> log
100
64
0
vudrav@vudrav-XL442:~/Рабочий стол/rayp1$ java AsmInt laba.asm 2> log
1235624
12DAA8
0
```

Другие тестовые примеры и их запуск

Файл test.asm
```
START JMP
0 0 0 0 0 0 0 0 0 0
:START
72 OUT ; out h
101 OUT
108 OUT 
108 OUT 
111 OUT 
33 OUT 
0 HALT ; end of file
```

Результат:
```
vudrav@vudrav-XL442:~/Рабочий стол/rayp1$ java AsmInt test.asm 2> log
Hello!
0
```

# Вывод

В ходе выполнения лабораторной работы я научился основам создания низкоуровневой виртуальной машины,
 использованию шаблонов enum для генерации большого количества однотипных классов(в данной работе их
  было 43), и трансляции асемблерного кода.
Использование в программе enum позволит легко модифицировать систему команд и ключевых слов 
так, что можно произвольно менять соответсвующие коды команд, их реализацию, добавлять новые команды.

Предполагаю, что для реальных задач(как если бы это было реальное устройство) было бы полезно
 расширить список команд средствами для взаимодействия с "виртуальными" устройствами: например
  памятью. Такое расширение позволит использовать на практике модельный ассемблер.
