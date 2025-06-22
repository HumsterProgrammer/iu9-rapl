package AsmInt;

import java.util.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

class BufferedStandartInputStream extends InputStream{
    LinkedList<Integer> buffer;
    InputStream src;
    
    BufferedStandartInputStream(InputStream src){
        this.src = src;
        buffer = new LinkedList<Integer>();
    }
    
    public int read() throws IOException{
        if(buffer.peek() != null){
            return buffer.pop();
        }
        buffer.add(src.read());
        while(src.available() > 0){
            buffer.add(src.read());
        }
        return buffer.pop();
    }
}

class MemorySpace{
	public static int MEMORY_SIZE = 1000;
	
	private static int[] memory;
	private static int end_of_program = 256;
	
	private static int IP;
	private static int SP;
	private static int FP;
	private static int RV;
	
	private static int END = Integer.MIN_VALUE; // код возврата
	
	private static TreeMap<Integer, KEYWORDS> OperatorsMap = KEYWORDS.MakeKeywordMap();
	
	static BufferedStandartInputStream bsis = new BufferedStandartInputStream(System.in);
	
	static InputStream virtualSTDIN = System.in;
    static PrintStream virtualSTDOUT = System.out;
    
    
    public static void initMemorySpace(){
        memory = new int[MEMORY_SIZE];
    }
    
	
	//======================debug info=====================
	private static PrintStream debugStream = System.err;
	private static ArrayList<Integer> stopPointerList = new ArrayList<Integer>();
	private static HashMap<Integer, String> metkaTable = new HashMap<Integer, String>();
	private static boolean isDebug = false;
	private static boolean isDebugSkip = false;
	private static boolean isFast = false;
	
	public static void addMetka(String name, int ptr){
	    metkaTable.put(ptr, name);
	}
	
    public static void setDebug(boolean flag){
        isDebug = flag;
    }
	public static void setDebugSkip(boolean flag){
	    isDebugSkip = flag;
	}
	public static void setDebugStream(PrintStream os){
	    debugStream = os;
	}
	public static void setFast(boolean flag){
	    isFast = flag;
	}
	
	private static void printProgram(){
	    for(int i = 256; i < end_of_program; i++){
		    System.err.printf("%d: ", i);
			if(memory[i] >= 0){
				System.err.print(memory[i]);
			}else{
				System.err.print(OperatorsMap.get(memory[i]));
			}
			if(metkaTable.containsKey(i)){
			    System.err.print("\t;"+metkaTable.get(i));
			}
			System.err.print("\n");
		}
	}
	private static void printDebugInfo(){
	    debugStream.printf("%d: ", IP);
        if(memory[IP] >= 0){
	        debugStream.print(memory[IP]);
        }else{
	        debugStream.print(OperatorsMap.get(memory[IP]));
        }
        if(metkaTable.containsKey(memory[IP])){
		    System.err.print("\t;"+metkaTable.get(memory[IP]));
	    }
        debugStream.printf("\n%d,%d,%d\n", SP, FP,RV);
        for(int i = SP; i < MEMORY_SIZE; i++){
	        debugStream.print(memory[i] + " ");
        }
        debugStream.print("\n");
	}
	public static void printFrame(int framePointer){
	    System.out.println(framePointer);
	    if(framePointer == 0){
	        return;
	    }
	    for(int i = SP; i < framePointer; i++){
	        System.out.print(memory[i] + " ");
	    }
	    System.out.println();
	    printFrame(memory[framePointer]);
	}
	
	public static boolean skipInputLine() throws IOException{
	    Scanner scan = new Scanner(System.in);
	    String command = scan.nextLine().trim();
	    if(command.equals("printmem")){
	        for(int i = 256; i < MEMORY_SIZE; i++){
	            System.err.printf("%d: ", i);
			    if(memory[i] >= 0){
				    System.err.println(memory[i]);
			    }else{
				    System.err.println(OperatorsMap.get(memory[i]));
			    }
	        }
	        return true;
	    }else if(command.equals("skip")){
	        isDebugSkip = true;
	        return false;
	    }else if(command.equals("printstack")){
	        int stackPointer = FP;
	        printFrame(FP);
	        return true;
	    }
	    return false;
	}
	
	public static void addStopPointer(int ptr){
	    stopPointerList.add(ptr);
	}
	
	//==================perform===========================
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
		SP = MEMORY_SIZE;
		FP = Integer.MIN_VALUE;
		RV = Integer.MIN_VALUE;
		
		if(isDebug){
		    printProgram();
		    debugStream.print("StopPointers:");
		    debugStream.println(stopPointerList);
	    	debugStream.println("=====PROGRAM START=====");
		}
		
		for(IP=256;END == Integer.MIN_VALUE; ){
		    if(isDebug){
		        if(!isDebugSkip){
		            printDebugInfo();
		        }
		        if(!isFast && !isDebugSkip){
		            try{
			            while(skipInputLine()){}
			        }catch(IOException e){}
		        }
		        
			    //scan.nextLine();
			    if(isDebugSkip){
			        if(stopPointerList.contains(IP)){
			            printDebugInfo();
			            isDebugSkip = false;
			            isFast = false;
			        }
			    }
		    }
		    if(IP<256){
		        virtualSTDOUT.println("address error:"+IP);
		        return -1;    
		    }
			//int previousIP = IP;
			//System.out.println(memory[IP]);
			if(memory[IP] < 0){
				int code = memory[IP++];
				OperatorsMap.get(code).action();
			}else{
				push(memory[IP++]);
			}
			
			//if(IP==previousIP) IP++;
		}
		if(END != 0){
		    printFrame(FP);
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
	
	//######################################################################
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
				if(a < 256){
				    virtualSTDOUT.println("segment fault: "+a);
				    END=-1;    
				}else{
				    push(get(a));
                } 
			}
		},
		SAVE(-36){
			public void action(){
				int v = pop();
				int a = pop();
				if(a < 256){
				    virtualSTDOUT.println("segment fault: "+a);
				    END=-1;    
				}else{
				    set(a, v);
                }   
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
		JMP(-13){
			public void action(){
				IP = pop();
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
					push(bsis.read());
				}catch(IOException e){
					System.err.println("ioexception while read");
					END=444;
				}
			}
		},
		OUT(-44){
			public void action(){
				System.out.write(pop());
				System.out.flush();
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
