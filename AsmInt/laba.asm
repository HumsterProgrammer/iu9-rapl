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
	GETFP 1 ADD SETFP RET ; здесь баг, что call возвращает на ту же команду, с которой перешел, соответственно снова выполняется call

;##############ERRORS#############
:INVALID_INPUT
	1 HALT

;##############MEMORY#############
:BUFFER_POINTER
