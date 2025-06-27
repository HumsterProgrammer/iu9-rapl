% Лабораторная работа № 5. «Управление памятью и сборка мусора»
% 10 июня 2025 г.
% Денис Кочетков, ИУ9-61Б


# Цель работы

Целью данной работы является изучение способов организации кучи и алгоритмов сборки мусора.

# Индивидуальный вариант

Алгоритм «пометить и подмести» + двунаправленный список блоков. Метод поиска — наилучший подходящий.

# Реализация и тестирование

Для реализации возможности динамического выделения памяти,
 была написана библиотека времени выполнения `DynMem.llpl`
  и расширен компилятор из лабораторной №4 соответсвующим конструкциями.

Библиотека `DynMem.llpl`
```
(ifndef dynmem_h
(const dynmem_h "=" 0)

(include "stdlib.llpl")

(comment 'Алгоритм «пометить и подмести» + двунаправленный список блоков.'
 'Метод поиска — наилучший подходящий.'
    START_OF_HEAP 'начало кучи'
    'Алгоритм работы:'
    'У меня в программе компилируются объектные ссылки'

    'Они заполнены какими-то объектами в куче.'
    'Если мы вызываем функции alloc, то мы пытаемся выделить объект в куче.'
    'То есть пытаемся выделить в куче объект, если вернулся 0, то проходимся по всей куче рекурсивно'
    'Освобождаем помеченные объекты.'
    'После чего снова делаем попытку, если так и не получилось, то возвращаем 0.'
)

(const USED__ "=" 1)
(const FREE__ "=" 0)
(const UNREACHED__ "=" 2)
(const UNSCANNED__ "=" 3)
(const SCANNED__   "=" 4)

(struct gc_info__
    (gc_info_size__ int)
    (gc_info_prev__ ptr)
    (gc_info_next__ ptr)
    (gc_info_flags__ int)
    (gc_info_nrefs__ int)
)

(var HEAP_SIZE 1 )

(function init_heap ()
    (var (heap ptr)(last ptr))
    
    (comment 'Инициализируем размер кучи как половина от свободного пространства')
    (heap "=" (L START_OF_HEAP))
    (HEAP_SIZE "=" (((L MEMORY_SIZE) "-" (L START_OF_HEAP)) "/" 2))
    (comment (HEAP_SIZE "=" 30))
    
    (comment "Мы инициализируем первую часть кучи как незанятую")
    ((heap "->" gc_info_flags__) "=" FREE__)
    ((heap "->" gc_info_size__)  "=" (L HEAP_SIZE))
    ((heap "->" gc_info_prev__)  "=" 0)
    ((heap "->" gc_info_next__)  "=" 0)
)


(function alloc__ (type nrefs)
    (var (block ptr))
    (comment 'Пытаемся выделить память в куче')
    (block "=" (call malloc (type "->" gc_info_size__)))
    
    (comment 'Если выделили успешно, возвращаем адрес')
    (if ((L block) "<>" 0)
        ((block "->" gc_info_nrefs__) "=" (L nrefs))
        (return (L block))
    )
    (comment 'Если выделить не получилось, то производим markAndSweep')
    (call markAndSweep)
    (comment 'Делаем еще одну попытку выделить и возвращаем ее результат')
    (block "=" (call malloc (type "->" gc_info_size__)))
    
    (comment 'Если выделили успешно, возвращаем адрес')
    (if ((L block) "<>" 0)
        ((block "->" gc_info_nrefs__) "=" (L nrefs))
        (return (L block))
    )
    (return 0)
)

(function malloc (size)
    (var(b ptr)(bestBlock ptr)(bestSize int))
    
    (comment "Начинаем с блока в начале кучи")
    (b "=" (L START_OF_HEAP))
    (bestSize "=" ("-" 1))
    (bestBlock "=" 0)
    (while ((L b) "<>" 0) (comment "Пока мы не достигли последнего блока(обозначили его размер как 0)")
        (if ( ((L (b "->" gc_info_flags__)) "==" FREE__)
            and ((L (b "->" gc_info_size__)) ">=" (L size)))
            
            (if ((L bestBlock) "==" 0)
                (bestSize "=" (L (b "->" gc_info_size__)))
                (bestBlock "=" (L b))
            else (comment "если предыдущий вариант хуже...")
                (if ((L bestSize) ">" (L (b "->" gc_info_size__))) 
                    (bestSize "=" (L (b "->" gc_info_size__)))
                    (bestBlock "=" (L b))
                )
            )
        )
        
        (b "=" (L (b "->" gc_info_next__)))
    )
    (asm "<malloc>")
    (if ((L bestBlock) "==" 0) (comment "Если мы не нашли ни одного блока, то возвращаем 0")
        (return 0)
    else (comment "Иначе разбиваем блок и вовзращаем адрес нового блока")
        
        (call split_block (L bestBlock) (L size))
        
        (comment "Инициализируем количество ссылок в выделенном блоке")
        
        (asm "<alloctrue>")
        (return (L bestBlock))
    )
)

(comment "Функция разбивает блок на две части, первая - свободная остается, вторая - занятой")
(function split_block (block size)
    (var (newBlock ptr))
    
    (comment "Если размер блока больше, чем нужно")
    (if ((L (block "->" gc_info_size__)) ">" (L size))
        (comment "Мы должны откусить от блока size слов вначале")
        (newBlock "=" ((L block) "+" (L size)))
        (asm "<splitinit>")
        
        
        (comment "block-1 block newBlock block+1")
        ((newBlock "->" gc_info_size__ ) "=" ((L (block "->" gc_info_size__)) "-" (L size)))
        ((newBlock "->" gc_info_flags__) "=" FREE__)
        ((newBlock "->" gc_info_next__ ) "=" (L (block "->" gc_info_next__)))
        ((newBlock "->" gc_info_prev__ ) "=" (L block))
        (if ( (L (block "->" gc_info_next__)) "<>" 0)
            (block (var (tmp ptr))
                (tmp "=" (L (newBlock "->" gc_info_next__)))
                ((tmp "->" gc_info_prev__) "=" (L newBlock))
            )
        )
        
        ((block "->" gc_info_size__ ) "=" (L size))
        
        ((block "->" gc_info_next__ ) "=" (L newBlock))
    )
    (comment "Если размер блока, не больше, то не делим")
    (comment "Помечаем блок как занятый, так как он все равно будет использоваться в куче."
        "Иначе, при полном совпадении GC будет думать, что блок пустой и выделять постоянно его")
    ((block "->" gc_info_flags__) "=" USED__)
    
    (asm "<splitresult>")
)

(function recursiveMark (objectPtr)
    (comment "Если ссылка не проинициализирована или блок уже просканирован, то завершаем функцию")
    
    (if ( ((L objectPtr) "==" 0) or ((L (objectPtr "->" gc_info_flags__)) "==" SCANNED__))
        (return 0)
    )
    
    (comment "Помечаем, что блок просканирован")
    ((objectPtr "->" gc_info_flags__) "=" SCANNED__)
    
    (comment "Проходим по всем его ссылкам")
    (block (var (i int))
        (i "=" 0)
        (while ((L i) "<" (L (objectPtr "->" gc_info_nrefs__)))
            (call recursiveMark (L ((objectPtr "->" gc_info__) "+" (L i))))
            (i "++") 
        )
    )
)
(function recursiveLocalRefs ()
    (var (fakePointer ptr)(i int)(refsCount int))
    
    (comment "Устанавливаем указатель фрейма для итерации")
    (fakePointer "=" (asm GETFP))
    (while ((L fakePointer) "<>" 0)
        (asm "<recloc>")
        (refsCount "=" (L ((L fakePointer) "-" 1)))
        
        (i "=" 1)
        (while ((L i) "<=" (L refsCount))
            (call recursiveMark (L (((L fakePointer) "-" 1)"-" (L i))))
            (i "++")
        )
        (fakePointer "=" (L (L fakePointer)))
    )
)


(function sweep (objectPtr)
    (var (nextBlock ptr))
    (comment "Мы идем по блокам с начала до конца. Мы помечаем этот блок свободным")
    
    ((objectPtr "->" gc_info_flags__) "=" FREE__)
    
    (comment "Повторяем так, пока не найдем достижимый блок или не дойдем до конца")
    (comment "Если следующий блок недостижымий")
    (nextBlock "=" (L (objectPtr "->" gc_info_next__)))
    
    (while (((L nextBlock) "<>" 0) and ((L (nextBlock "->" gc_info_flags__)) "<>" SCANNED__))
        (comment "то расширяем исходный блок на его размер.")
        (  (objectPtr "->" gc_info_size__) "=" (
            (L (objectPtr "->" gc_info_size__)) 
                "+"
            (L (nextBlock "->" gc_info_size__)))
        )
        (nextBlock "=" (L (nextBlock "->" gc_info_next__)))
    )
    (( objectPtr "->" gc_info_next__) "=" (L nextBlock))
    (if ((L nextBlock) "<>" 0)
        ((nextBlock "->" gc_info_prev__) "=" (L objectPtr))
    )
)

(function markAndSweep ()
    (var (b ptr)(i int))
    (asm "<markAndSweepStart>")
    (comment "Пометка всех объектов в куче, как недостижимых.")
    (b "=" (L START_OF_HEAP))
    (while ((L b) "<>" 0)
        ((b "->" gc_info_flags__) "=" UNREACHED__)
        (b "=" (L (b "->" gc_info_next__)))
    )
    
    (comment "Перебор всех ссылок в корневом множестве (глобальные и локальные ref‐переменные):")
    
    (comment "Перебор глобальных переменных")
    (b "=" (global_refs__ "+" 1)) (comment 'указатель на первую ссылку')
    (while ((L b) "<=" (global_refs__ "+" (L global_refs__)))
        (call recursiveMark (L (L b)))
        (b "++")
    )
    (comment "Перебор локальных переменных")
    (call recursiveLocalRefs)
    
    (asm "<markAndSweepMarked>")
    (comment "Освобождение памяти, занятой объектами, помеченными как недостижимые (фаза подметания)")
    (b "=" (L START_OF_HEAP))
    (while ((L b) "<>" 0)
        (if ((L (b "->" gc_info_flags__)) "==" UNREACHED__)
            (call sweep (L b))
        )
        (b "=" (L (b "->" gc_info_next__)))
    )
    (asm "<markAndSweepFinal>")
)
)
```

Файл компилятора `compilerLLPL.ref`
```
*$FROM LibraryEx
$EXTERN ArgList, Map, LoadExpr;

/*

НУИЯП++ с виртуальным множественным наследованием.

*/

/*Точка входа. Передает аргументы в функцию Main*/
$ENTRY Go{
	= <Main <ArgList>> <Prout ';' <Dgall>>
}

/*Выводит результат для каждого переданного файла в последовательный вывод*/
Main{
	(e.ProgName) e.Args, 
	<InitCompiler><ParseCommandArgs e.Args> : e.FileList = 
	    <Prout <Map LLPL_ParseFile e.FileList>> /*<Prout <Dgall>>*/;
}
InitCompiler{
    /**/ = <Br libPath '=' '.'>
}

/*В время компиляции указывается путь к библиотекам. 
команда include будет пытаться искать библиотеки именно в этой директории.
 Сделал так, чтобы не мучаться с адресациями директорий. 
 Если не указать будет по-умолчанию использоваться текущая директория*/
ParseCommandArgs{
    /*empty*/ = /*empty*/;
    ('lib=' e.LibPath) e.Tail = <Br libPath '=' e.LibPath> <ParseCommandArgs e.Tail>;
    (e.FileName) e.Tail = (e.FileName) <ParseCommandArgs e.Tail>
}


/*<ParseFile e.FileName> = print all tokens*/
$ENTRY LLPL_ParseFile{
	(e.FileName) = <MakeBaseName e.FileName> <CompileLLPL <LoadExpr e.FileName>>
}

$ENTRY CompileLLPL{
    e.AST = <Compile <Ord <LLPL_MacroPreprocess e.AST>>> '\n';
}

/*Предобработка макроопераций */
$ENTRY LLPL_MacroPreprocess{
    /*empty*/ = /*empty*/;
    s.smth = s.smth;
    
    (comment e.smth) = /*erase*/; /*Очищает комментарии*/
    
    (include s.FileName) = 
        <LLPL_MacroPreprocess <LoadExpr <Cp libPath>'/'<Explode s.FileName>>>; /*(include "lib.h")*/
    
    /*Немного костыльно: вместо сишного define использую const,
     которые теперь можно объявить где угодно в программе*/
    (const s.Name "=" t.ConstExpr), <EvalExpr t.ConstExpr> : s.Value =
	    <AddGlobal s.Name s.Value> <Prout ';' (const s.Name "=" s.Value) >;
    
    /*вместо define будут использоваться константы*/
    (ifdef s.Name e.Body), <GetConst s.Name> : {
        Found e.Value = <LLPL_MacroPreprocess e.Body>;
        NotFound = /*<Prout s.Name 'not defined'>*/;
    };
    (ifndef s.Name e.Body), <GetConst s.Name> : {
        Found e.Value = /*<Prout s.Name 'defined'>*/;
        NotFound = <LLPL_MacroPreprocess e.Body>;
    };
    
    
    (e.smth) = (<Map LLPL_MacroPreprocess e.smth>);
    e.smth = <Map LLPL_MacroPreprocess e.smth>;
}


/*Делает базовое имя файла. Задумывалось как добавление уникальности в метки и прочее. Не было добавлено.*/
MakeBaseName{
	e.FileName '.' e.Ref = <MakeBaseName e.FileName>;
	e.FileName = <Br blockName '=' e.FileName><Br blockNumber '=' 0>;
}


/*Функция компиляции дерева программы, считанного из файла*/
Compile{
	e.Program,
	<DynMem_CountRefs e.Program 0> : s.Count = 
	<Prout <LLPL_GenDefinition (var global_refs__ 1 "=" s.Count)>> <Map LLPL_GenDefinition e.Program>;
}

DynMem_CountRefs{
    s.Count = s.Count;
    (refs e.Names) e.Tail s.Count = <DynMem_CountRefs e.Tail <Add <Count e.Names> s.Count>>;
    t.Definition e.Tail s.Count = <DynMem_CountRefs e.Tail s.Count>;
}

/*Функция компиляции определений*/
$ENTRY LLPL_GenDefinition{
	
	(struct s.Name e.Fields), <GenStruct (e.Fields) 0> : s.Size = 
	    <AddGlobal s.Name s.Size> ';' (struct s.Name e.Fields)'\n';
	
	(var s.Name "=" e.Definition) =
	    '\n:_' <Explode s.Name> 
	    '\n' <GenVar (e.Definition) <Count e.Definition>> 
	    '; '(var s.Name "=" e.Definition)'\n';
	
	(var s.Name t.ConstExpr), <EvalExpr t.ConstExpr> : s.Value = 
	    '\n:_' <Explode s.Name> '\n' <GenVar () s.Value> '; ' (var s.Name s.Value)'\n';
	
	(var s.Name t.ConstExpr "=" e.Definition), <EvalExpr t.ConstExpr> : s.Value = 
	    '\n:_' <Explode s.Name> 
	    '\n' <GenVar (e.Definition) s.Value> 
	    '; '(var s.Name s.Value "=" e.Definition)'\n';
	
	(function s.Name (e.Params) e.Body) = 
	    '\n'<GenFunction s.Name (e.Params) e.Body> '\n';
	
	(const s.Name "=" t.ConstExpr), <EvalExpr t.ConstExpr> : s.Value =
	    <AddGlobal s.Name s.Value> <Prout ';' (const s.Name "=" s.Value) >;
	 
	/*======================Dyn Mem===========================*/
	(refs e.Names) = <Map DynMem_GenRefs e.Names>;
	
	(dynvar s.Name
	    (refs e.Refs)
	    e.Fields
	) = <LLPL_GenDefinition
	    (struct s.Name 
	        ("-" gc_info__)
	        <Map DynMem_DynvarRefs e.Refs>
	        e.Fields
	    )>
	    <LLPL_GenDefinition (const <Implode 'NREFS_' <Explode s.Name> '__'> "=" <Count e.Refs>)>;
	
	(dynvar s.Name
	    e.Fields
	) = <LLPL_GenDefinition
	    (struct s.Name 
	        ("-" gc_info__)
	        e.Fields
	    )>
	    <LLPL_GenDefinition (const <Implode 'NREFS_' <Explode s.Name> '__'> "=" 0)>;
	
	/*=======================OOP==============================*/
	(class s.Name ()
	    (fields e.Fields)
	    e.Methods
	) = <Map LLPL_GenDefinition
	    (struct <Implode <Explode s.Name>'_core__'>
	        ("-" 1)
	        e.Fields
	    )
	    (struct <Implode <Explode s.Name>'_class__'>
	        <Map (LLPLpp_GenClassVirtualTableField s.Name) e.Methods>
	    )
	    (struct s.Name
	        ("-" <Implode <Explode s.Name>'_core__'>)
	    )
	    <Map (LLPLpp_GenClassVirtualFunction s.Name) e.Methods>
	    (var <Implode <Explode s.Name>'_vtbl__'> <Implode <Explode s.Name>'_class__'> "="
	        <Map (LLPLpp_GenClassVirtualMethodMetka s.Name) e.Methods>
	    )
	    (function <Implode 'init__'<Explode s.Name>> (thisPtr)
	        ((L thisPtr) "=" <Implode <Explode s.Name>'_vtbl__'>)
	        <Map (LLPLpp_GenInit s.Name) >
	    )
	    >;
	
	/*класс с наследованием*/
	(class s.Name (s.Parent e.Base)
	    (fields e.Fields)
	    e.Methods
	), <LLPLpp_GetAllAncestors s.Name s.Parent e.Base> : e.Ancesstors
	
	 = <Map LLPL_GenDefinition
	    (struct <Implode <Explode s.Name>'_core__'>
	        ("-" 1)
	        e.Fields
	    )
	    (struct <Implode <Explode s.Name> '_class__'>
	        <Map (LLPLpp_GenClassVirtualFunctionVTableEntry s.Name) e.Methods>
	        <Map (LLPLpp_GenClassDeltas s.Name) e.Ancesstors>
	    )
	    (struct s.Name
	        ("-" <Implode <Explode s.Name>'_core__'>)
	        <Map (LLPLpp_GenClassVirtualTableFieldsWithDescent s.Name) e.Ancesstors>
	    )
	    <Map (LLPLpp_GenClassVirtualFunction s.Name) e.Methods>
	    (var <Implode <Explode s.Name> '_vtbl__'> <Implode <Explode s.Name> '_class__'> "="
	        <Map (LLPLpp_GenMethodsMetkas s.Name 0) e.Methods>
	        <Map (LLPLpp_GenAncesstorsMetkas s.Name) e.Ancesstors>
	    )
	    <Map (LLPLpp_recursiveVtbl s.Name) e.Ancesstors>
	    (function <Implode 'init__'<Explode s.Name>> (thisPtr)
	        ((L thisPtr) "=" <Implode <Explode s.Name>'_vtbl__'>)
	        <Map (LLPLpp_GenInit s.Name) e.Ancesstors>
	    )
	>;
}
$ENTRY DynMem_GenRefs{
    s.Name = <Prout <LLPL_GenDefinition (var s.Name 1 "=" 0)>>;
}
$ENTRY DynMem_DynvarRefs{
    s.Ref = (s.Ref 1)
}

$ENTRY LLPLpp_GenClassDescent{
    s.Name s.Pred = (<Implode <Explode s.Name>'__'<Explode s.Pred>> s.Pred);
}

$ENTRY LLPLpp_GenClassVirtualTableField{
    s.ClassName (method s.Name (this e.Args) e.Code) = 
        (<Implode <Explode s.ClassName> '_' <Explode s.Name>> VTableEntry);
}
$ENTRY LLPLpp_GenClassVirtualFunction{
    s.ClassName (method s.Name (this e.Args) e.Code),
    <Implode <Explode s.ClassName>'__'<Explode s.Name>> : s.MethodName,
    <LLPLpp_AddClassMethod s.ClassName s.MethodName> : e.1
     = 
        (function s.MethodName 
            (this e.Args)
            e.Code
        );
}
$ENTRY LLPLpp_GenClassVirtualFunctionVTableEntry{
    s.ClassName (method s.Name (this e.Args) e.Code) =
        (<Implode <Explode s.ClassName>'_'<Explode s.Name>> VTableEntry);
}

$ENTRY LLPLpp_GenClassVirtualMethodMetka{
    s.ClassName (method s.Name (this e.Args) e.Code) =
        <Implode <Explode s.ClassName>'__'<Explode s.Name>> 0;
}
$ENTRY LLPLpp_GenClassVirtualTableFieldsWithDescent{
    s.Name s.Ancesstor = 
        (<Implode <Explode s.Name>'__'<Explode s.Ancesstor>> <Implode <Explode s.Ancesstor>'_core__'>)
}
$ENTRY LLPLpp_GenClassDeltas{
    s.Name s.Ancesstor = (<Implode <Explode s.Name>'__'<Explode s.Ancesstor>'_offset__'> 1)
}
$ENTRY LLPLpp_GenMethodsMetkas{
    s.Name s.Delta (method s.Method (e.Args) e.Body) = 
        <Implode <Explode s.Name>'__'<Explode s.Method>> s.Delta;
}
$ENTRY LLPLpp_GenMethodsMetkasByName{
    s.Name s.Delta s.Method = s.Method s.Delta;
}
$ENTRY LLPLpp_GenAncesstorsMetkas{
    s.Name s.Ancesstor = <Implode <Explode s.Name>'__'<Explode s.Ancesstor>>;
}
$ENTRY LLPLpp_GetAllAncestors{
    s.Name s.Parent e.Base,
    <Map (LLPLpp_AddAncesstors s.Name) s.Parent e.Base> : e.1
     = <Cp <Implode <Explode s.Name>'__ancesstors__'>>;
}
$ENTRY LLPLpp_AddAncesstors{
    s.Name s.Parent,
    <Implode <Explode s.Name>'__ancesstors__'> : s.BrName,
    <Unique <Dg s.BrName> s.Parent <Cp <Implode <Explode s.Parent> '__ancesstors__'>>> : e.Ancesstors
     = <Br s.BrName '=' e.Ancesstors>;
}
$ENTRY LLPLpp_AddClassMethod{
    s.Name s.Method,
    <Implode <Explode s.Name> '__methods__'> : s.BrName = <Br s.BrName '=' <Cp s.BrName> s.Method>
}
Unique{
    s.x e.1 s.x e.2 = s.x <Unique e.1 e.2>;
    s.x e.1 = s.x <Unique e.1>;
    /*empty*/ = /*empty*/;
}

$ENTRY LLPLpp_recursiveVtblDeltas{
    s.BaseName s.Delta s.Ancesstor = (<Implode <Explode s.BaseName>'__'<Explode s.Ancesstor>> "-" s.Delta)
}

$ENTRY LLPLpp_recursiveVtbl{
    s.BaseClassName s.Ancesstor,
    <Cp <Implode <Explode s.Ancesstor>'__methods__'>> : e.Methods,
    <Implode <Explode s.BaseClassName> '__' <Explode s.Ancesstor>> : s.Delta,
    <Cp <Implode <Explode s.Ancesstor>'__ancesstors__'>> : e.ParentAncesstors
     = 
    (var <Implode <Explode s.BaseClassName>'__'<Explode s.Ancesstor>'_vtbl__' >
     <Implode <Explode s.Ancesstor>'_class__'>
        "=" 
        <Map (LLPLpp_GenMethodsMetkasByName s.BaseClassName s.Delta) e.Methods>
        <Map (LLPLpp_recursiveVtblDeltas s.BaseClassName s.Delta) e.ParentAncesstors>
    );
}
$ENTRY LLPLpp_GenInit{
    s.BaseClassName s.Ancesstor = 
    (call <Implode 'init__'<Explode s.Ancesstor>> 
        (thisPtr "->" <Implode <Explode s.BaseClassName>'__'<Explode s.Ancesstor>>));
}

/**************************OPERATORS*******************************/

/*Отдельная функция для компиляции определения функции в программе*/
GenFunction{
    /*=================Dyn Mem==================*/
    s.Name (e.Params) (var (refs e.Refs) e.Vars) e.Body, 
        <SetFunctionName s.Name e.Params> : e.tmp =
	        ':_'<Explode s.Name>'\n' 
	        <GenFunctionProlog (e.Params)(e.Refs)(e.Vars)>
	        <GenBody e.Body>
	        <GenFunctionEpilog>;


    /*=================BASE=====================*/
	s.Name (e.Params) (var e.Vars) e.Body, 
	    <SetFunctionName s.Name e.Params> : e.tmp =
	        ':_'<Explode s.Name>'\n' 
	        <GenFunctionProlog (e.Params)()(e.Vars)>
	        <GenBody e.Body>
	        <GenFunctionEpilog>;
    
    s.Name (e.Params) e.Body, 
        <SetFunctionName s.Name e.Params> : e.tmp =
            ':_'<Explode s.Name>'\n' 
            <GenFunctionProlog (e.Params)()()>
            <GenBody e.Body> 
            <GenFunctionEpilog>;
    
}

/*Функция, запоминания имени функции и количества параметров*/
SetFunctionName{
	s.Name e.Params = <Br function '=' s.Name <Count e.Params>>
}

/*Проходит по всем параметрам, запоминая их смещения в копилку.*/
SetDeltaParams{
    s.Number = ;
    s.Name s.Number = <AddLocal s.Name '+' s.Number >;
    s.Name e.Params s.Number = <AddLocal s.Name '+' s.Number > <SetDeltaParams e.Params <Add 1 s.Number>>;
}

/*Проходит по всем переменным, запоминая их смещения в копилку*/
SetDeltaVars{
    s.Number = s.Number;
    (s.Name t.Expr) s.Number, <Add <EvalExpr t.Expr> s.Number> : s.NewNumber =
         <AddLocal s.Name '-' s.NewNumber ><SetDeltaVars s.NewNumber>;
    e.Params (s.Name t.Expr) s.Number, <Add <EvalExpr t.Expr> s.Number> : s.NewNumber =
         <AddLocal s.Name '-' s.NewNumber> <SetDeltaVars e.Params s.NewNumber>;
}

SetDeltaRefs_rec{
    s.Number = s.Number;
    s.Name s.Number =
         <AddLocal s.Name '-' <Add 2 s.Number>> <Add 1 s.Number>;
    e.Params s.Name s.Number =
         <AddLocal s.Name '-' <Add 2 s.Number>> <SetDeltaRefs_rec e.Params <Add 1 s.Number>>;
}


SetDeltaRefs{
    e.Refs, <SetDeltaRefs_rec e.Refs 0> : s.Count
     = s.Count (s.Count <Fill s.Count 0>);
}

Fill{
    0 s.Symbol = /**/;
    s.Count s.Symbol = s.Symbol <Fill <Sub s.Count 1> s.Symbol>;
}

/*Генерирует пролог функции*/
GenFunctionProlog{
	(e.Params)(e.Refs)(e.Vars),
	<SetDeltaRefs e.Refs> : s.RefsDelta (e.CompiledInit),
	<NewLocal> (<SetDeltaParams e.Params 2>)
	(<SetDeltaVars e.Vars <Add s.RefsDelta 1>>) : (e.1) (s.LocalVarsRes),
	<Sub s.LocalVarsRes <Add s.RefsDelta 1>> :
	{
        0, <SetLocalCount 0> : e.2 = GETFP GETSP SETFP '\n' e.CompiledInit ';refs compile\n';
        s.Number, <SetLocalCount s.Number> : e.2 =
         GETFP GETSP SETFP '\n' e.CompiledInit '\n' GETSP s.Number SUB SETSP '\n';
    };
}

/*Вспомогательная функция, считающая количество определений в строке*/
Count{
    /**/ = 0;
    t.1 e.Tail = <Add 1 <Count e.Tail>>;
}


/*Генерирует тело блока, тело функции, тело условий или тело цикла*/
GenBody{
	t.Operator e.Tail = <GenOperator t.Operator> <GenBody e.Tail>;
	/*empty*/ = /*empty*/;
}

/*Генерирует эпилог функции*/
GenFunctionEpilog{
	/*empty*/, <EndLocal> : e.1, <Cp function> : {
	    s.Name 0 = ':'<EpilogName s.Name>'\n' GETFP SETSP SETFP RET;
	    s.Name 1 = ':'<EpilogName s.Name>'\n' GETFP SETSP SETFP RET2;
	    s.Name s.Count = ':'<EpilogName s.Name>'\n' GETFP SETSP SETFP s.Count RETN JMP;
	};
}

/*Генерирует метку эпилогая функции*/
GenFunctionEpilogMetka{
	/*empty*/, <Cp function> : s.Name s.Number  = <EpilogName s.Name>
}

/*Генерирует имя эпилога*/
EpilogName{
	s.FunctionName = '__epilog_' <Explode s.FunctionName>
}


/*Конкатенирует к базовому имени уникальный номер(AUTOINCREMENT(0,1)) для создания уникальной метки*/
GenConsMetka{
	e.BaseName, <Dg blockNumber> : s.Number, <Cp function> : s.Name s.Num
	= e.BaseName '_' <Explode s.Name> '_' <Cp blockName>'_'<Symb s.Number> 
	<Br blockNumber '=' <Add s.Number 1>>
}


/*Генерация оператора блока*/
$ENTRY GenOperator{
    (t.Expr "++") = 
        <LLPL_GenExpr t.Expr 0>     /*... addr*/
        DUP                         /*... addr addr*/
        LOAD                        /*... addr value*/
        1 ADD                       /*... addr (value+1)*/
        SAVE                        /*...*/
        ;
    (t.Expr "--") = 
        <LLPL_GenExpr t.Expr 0>     /*... addr*/
        DUP                         /*... addr addr*/
        LOAD                        /*... addr value*/
        1 SUB                       /*... addr (value+1)*/
        SAVE                        /*...*/
        ;
    (t.Target "+=" t.Value) = 
        <LLPL_GenExpr t.Target 0>   /*... addr*/
        DUP                         /*... addr addr*/
        LOAD                        /*... addr &addr*/
        <LLPL_GenExpr t.Value 0>    /*... addr &addr value*/
        ADD                         /*... addr (&addr+value)*/
        SAVE                        /*...*/
        ;  
    (t.Target "-=" t.Value) = 
        <LLPL_GenExpr t.Target 0>   /*... addr*/
        DUP                         /*... addr addr*/
        LOAD                        /*... addr &addr*/
        <LLPL_GenExpr t.Value 0>    /*... addr &addr value*/
        SUB                         /*... addr (&addr-value)*/
        SAVE                        /*...*/
        ;  
    (t.Target "*=" t.Value) = 
        <LLPL_GenExpr t.Target 0>   /*... addr*/
        DUP                         /*... addr addr*/
        LOAD                        /*... addr &addr*/
        <LLPL_GenExpr t.Value 0>    /*... addr &addr value*/
        MUL                         /*... addr (&addr*value)*/
        SAVE                        /*...*/
        ; 
    (t.Target "/=" t.Value) = 
        <LLPL_GenExpr t.Target 0>   /*... addr*/
        DUP                         /*... addr addr*/
        LOAD                        /*... addr &addr*/
        <LLPL_GenExpr t.Value 0>    /*... addr &addr value*/
        DIV                         /*... addr (&addr/value)*/
        SAVE                        /*...*/
        ;   
    /*====================base LLPL=============================*/     

	(t.Target "=" t.Value) = 
	    <LLPL_GenExpr t.Target 0>           /*... addr*/
	    <LLPL_GenExpr t.Value 1>            /*... addr value*/
	    SAVE                           /*... addr value SAVE*/
	    ';' (t.Target "=" t.Value) '\n';
	
	(call t.Name e.Arguments) = 
	    <LLPL_GenExpr (call t.Name e.Arguments) 0>   /*... value*/
	    DROP                                         /*... value DROP*/
	    ';' (call t.Name e.Arguments) '\n';
	
	(if t.BoolExpr e.TrueBody else e.FalseBody),
		(<GenConsMetka 'if_true'>)
		(<GenConsMetka 'if_false'>)
		(<GenConsMetka 'if_out'>) : (e.true)(e.false)(e.out)
	 = <GenBool t.BoolExpr (e.true)(e.false)> '\n'
	 	':'e.true'\n' <GenBody e.TrueBody> e.out' JMP \n'
		':'e.false'\n' <GenBody e.FalseBody> ':'e.out '\n';
		
	(if t.BoolExpr e.TrueBody),
		(<GenConsMetka 'if_true'>)(<GenConsMetka 'if_out'>) : (e.true)(e.out)
	 = <GenBool t.BoolExpr (e.true)(e.out)> '\n'
	 	':'e.true'\n' <GenBody e.TrueBody> ':'e.out'\n';
	 	
	(while t.BoolExpr e.Body),
		(<GenConsMetka 'while_loop'>)
		(<GenConsMetka 'while_true'>)
		(<GenConsMetka 'while_out'>) : (e.loop)(e.true)(e.out)
	 = ':'e.loop'\n' <GenBool t.BoolExpr (e.true)(e.out)> '\n'
		':'e.true'\n' <GenBody e.Body> e.loop' JMP\n' ':'e.out'\n';
	
	(return t.Expr) = 
	    <LLPL_GenExpr t.Expr 0>         /*... value*/
	    SETRV                           /*...              ;RV=value*/
	    <GenFunctionEpilogMetka>' '     /*... &epilog      ;RV=value*/
	    JMP                             /*... &epilog JMP  ;RV=value*/
	    ';' (return t.Expr)'\n';
	
	(asm e.ASM) = e.ASM ';ASM\n';
	
	(block (var e.LocalVars) e.Code), 
	(<NewLocal>
	    <SetLocalCount <SetDeltaVars e.LocalVars <Cp localCount>>>
	    <Cp localCount>) : (e.1 s.BaseNumber ), 
	(<GenBody e.Code>)(<EndLocal>) : (e.GeneratedCode) (e.2),
	 <Sub s.BaseNumber <Cp localCount>> : s.Delta
	    = GETSP s.Delta SUB SETSP '\n' /*выделение памяти под локальные переменные*/
	      e.GeneratedCode              /*сгенерированный код блока                */
	      GETSP s.Delta ADD SETSP      /*очищение памяти от локальных переменных  */
	      '; block \n';
	
	(block e.Code) = <GenBody e.Code> '; block\n';
	
	
	/*================DynMem====================*/
	
	/*?DynMem block?*/
	
	(t.LeftExpr ":-" t.RightExpr) = <GenOperator (t.LeftExpr "=" t.RightExpr)>;
	
	(gc-alloc t.Expr s.Type) = 
	    <GenOperator (t.Expr "=" (call alloc__ s.Type <Implode 'NREFS_' <Explode s.Type> '__'>))>;
	
	(ref-return t.Expr) = <GenOperator (return t.Expr)>;
	
	/*==================OOP=======================*/
	(init t.ObjectPtr s.ClassName) = 
	    <GenOperator (call <Implode 'init__'<Explode s.ClassName>> t.ObjectPtr)>;
	
	(mcall t.Object s.Method e.ArgsExpr) = 
	    <LLPL_GenExpr (mcall t.Object s.Method e.ArgsExpr) 0>
	    DROP;
}


/**********************BOOOOOL EXPRS*******************************/
/*Генерация булевских операций с добавлением переходов на положительную ветвь и отрицательную ветвь*/

/* t.BoolExpr (e.TrueMetka) (e.FalseMetka) == e.Program */
GenBool{
	TRUE (e.TrueMetka)(e.FalseMetka) = e.TrueMetka ' JMP ';
	FALSE (e.TrueMetka)(e.FalseMetka) = e.FalseMetka ' JMP ';
	
	(t.LeftExpr and t.RightExpr) (e.TrueMetka)(e.FalseMetka),
	    <GenConsMetka 'internal'> : e.InternalMetka
	=   <GenBool t.LeftExpr (e.InternalMetka)(e.FalseMetka)> '\n'
	    ':' e.InternalMetka '\n'
	    <GenBool t.RightExpr (e.TrueMetka)(e.FalseMetka)>;
	
	(t.LeftExpr or t.RightExpr) (e.TrueMetka)(e.FalseMetka),
	    <GenConsMetka 'internal'> : e.InternalMetka
	=   <GenBool t.LeftExpr (e.TrueMetka)(e.InternalMetka)> '\n'
	    ':' e.InternalMetka '\n'
	    <GenBool t.RightExpr (e.TrueMetka)(e.FalseMetka)>;
	
	(t.LeftExpr s.RelOp t.RightExpr) (e.TrueMetka)(e.FalseMetka),
	("<" JLT)(">" JGT)("==" JEQ)("<=" JLE)(">=" JGE)("<>" JNE) : e.1(s.RelOp s.Command)e.2 
		= <LLPL_GenExpr t.LeftExpr 0> 
		<LLPL_GenExpr t.RightExpr 1> 
		CMP 
		e.TrueMetka ' ' s.Command e.FalseMetka ' ' JMP;
	
	(not t.BoolExpr)(e.TrueMetka)(e.FalseMetka) = <GenBool t.BoolExpr (e.FalseMetka)(e.FalseMetka)>;
}

/********************GLOBAL CONSTRUCTIONS**************************/

/*Генерирует определение переменной*/
GenVar{
	(e.Smth) 0 = /*empty*/;
	() s.Count = '0 ' <GenVar ()<Sub s.Count 1>>;
	(t.Expr e.Tail) s.Count, <EvalExpr t.Expr> : e.Value = e.Value <GenVar (e.Tail) <Sub s.Count 1>> 
}


/*Генерирует определение структуры данных*/
/* (t.Fields+) s.Size == s.Size */
GenStruct{
	(("-" t.ConstExpr) e.Tail) s.Size, 
	    <Add <EvalExpr t.ConstExpr> s.Size> : s.NewSize = <GenStruct (e.Tail) s.NewSize>;
	
	((s.Name t.ConstExpr) e.Tail) s.Size, 
	<Add <EvalExpr t.ConstExpr> s.Size> : s.NewSize =
	         <AddGlobal s.Name s.Size> <GenStruct (e.Tail) s.NewSize>;
	
	(/*empty*/) s.Size = s.Size;
}

/*Вспомогательная функция: вычисляет значение константного выражения. 
Необходимо для генерации структур и прочих.*/
EvalExpr{
	s.Name, <Type s.Name> : 'W' e.1, <GetConst s.Name> : Found e.Value, 
	e.Value : {
		'-' s.Number = <Mul s.Number '-1'>;
		s.Number = s.Number;
	};
	
	s.Number , <Type s.Number> : 'N' e.T = s.Number;
	
	s.Name = '_' <Explode s.Name> ' ';
 
	("-" t.Expr) = <Mul <EvalExpr t.Expr> '-' 1>;
	
	(t.LeftExpr s.BinOp t.RightExpr), ("+" Add) ("-" Sub) ("*" Mul) ("/" Div) ("%" Mod)
	: e.1 (s.BinOp s.Function) e.2 = <Mu s.Function <EvalExpr t.LeftExpr > <EvalExpr t.RightExpr>>;
	
	(asm e.ASM) = '\n' e.ASM '\n';
}


/*Добавление глобальной константы*/
AddGlobal{
	s.Name s.Value = <Br global '=' <Dg global> (s.Name s.Value)>;
}
/*Добавление нового стека локальных псевдонимов*/
NewLocal{
	/*empty*/ = <Br local '=' <Cp local>>;
}
/*Задает количество памяти под локальные переменные*/
SetLocalCount{
    s.Number = <Br localCount '=' s.Number>;
}
/*Получает это количество*/
GetLocalCount{
    /**/ = <Cp localCount>
}
/*Убирает с стека таблицу локальных переменных*/
EndLocal{
	/*empty*/ = <Dg local><Dg localCount>;
}
/*Добавляет новый локальный псевдоним*/
AddLocal{
	s.Name e.Value = <Br local '=' <Dg local> (s.Name e.Value)>;
}

/******************ARITH*EXPR**********************/


/*Компилирует арифметическое выражение*/
/*LLPL_GenExpr t.ArithExpr == e.Program
	e.VarList ::= (s.Name s.Status s.Number)*
	s.Status ::= A|L
*/

$ENTRY LLPL_GenExpr{

    /*Синтаксический сахар!: Обращение к полю структуры(по номеру или псевдониму)*/
	(t.AddrExpr "->" t.FieldExpr) s.Depth =
	    <LLPL_GenExpr ((L t.AddrExpr) "+" t.FieldExpr) s.Depth>;
	

    /*===================BASE LLPL==================*/

	s.Name s.Depth, <GetConst s.Name> : Found e.Value, 
	e.Value : {
		'-' s.Number = s.Number NEG;
		s.Number = s.Number;
	};
	
	s.Name s.Depth, <GetLocal s.Name> : Found e.Value, 
	e.Value : {
		'+' s.Number = GETFP s.Number ADD;
		'-' s.Number = GETFP s.Number SUB;
	};
	
	s.Number s.Depth, <Type s.Number> : 'N' e.T = s.Number;
	
	s.Name s.Depth = '_' <Explode s.Name> ' ';
 
	("-" t.Expr) s.Depth = <LLPL_GenExpr t.Expr s.Depth>  NEG;
	(L t.Expr)   s.Depth = <LLPL_GenExpr t.Expr s.Depth>  LOAD;
	
	(call t.FuncExpr e.ArgsExpr) s.Depth,
	<GenExprFunctionArgs e.ArgsExpr s.Depth> : e.CompiledArgs s.NewDepth =
        e.CompiledArgs
        <LLPL_GenExpr t.FuncExpr s.NewDepth> 
        CALL 
        GETRV;
	
	(t.LeftExpr "=" t.RightExpr) s.Depth = 
	    <LLPL_GenExpr t.LeftExpr s.Depth>            /*... addr*/
	    <LLPL_GenExpr t.RightExpr <Add s.Depth 1>>   /*... addr value*/
	    DUP                                          /*... addr value value*/
	    ROT ROT                                      /*... value addr value*/
	    SAVE                                         /*... value*/;
	
	
	(t.LeftExpr s.BinOp t.RightExpr) s.Depth,
	("+" ADD) ("-" SUB) ("*" MUL) ("/" DIV) 
	("%" MOD) ("&" AND) ("|" OR) ("~" XOR) : e.1 (s.BinOp s.GenOp) e.2 = 
	    <LLPL_GenExpr t.LeftExpr s.Depth>            /*... value1*/
	    <LLPL_GenExpr t.RightExpr <Add s.Depth 1>>   /*... value1 value2*/
	    s.GenOp;                                     /*... value1 value2 operation*/
	
	(asm e.ASM) s.Depth = '\n' e.ASM '\n';
	
	/*=============?DynMem let?=================*/
	
	(let (var e.LocalVars) e.Code t.Expr) s.Depth,
	<Add <Cp localCount> s.Depth> : s.Number,
	(<NewLocal>
	    <SetLocalCount <SetDeltaVars e.LocalVars s.Number>>
	     <Cp localCount>) : (e.1 s.BaseNumber ), 
	(<GenBody e.Code> <LLPL_GenExpr t.Expr 0>)(<EndLocal>) : (e.GeneratedCode) (e.2),
	 <Sub s.BaseNumber s.Number> : s.Delta =
	    GETSP s.Delta SUB SETSP ';start let\n'  /*... empt1 empt2 ... enptn*/
	    e.GeneratedCode                         /*... var1 var2 ... varn result*/
	    /*smth like RETN*/
	    s.Delta                                 /*... var1 var2 ... varn result n*/
	    GETSP                                   /*... var1 var2 ... varn result n &n*/
        ADD                                     /*... var1 var2 ... varn result &var2*/
        DUP                                     /*... var1 var2 ... varn result &var2 &var2*/
        ROT                                     /*... var1 var2 ... varn &var2 result &var2*/
        SAVE                                    /*... var1 result ... varn &var2*/
        SETSP                                   /*... var1 result*/
        SDROP  '; let\n'                        /*... result*/
	;
	(let e.Code t.Expr) s.Depth = 
	    <GenBody e.Code> <LLPL_GenExpr t.Expr> '; let\n';
	    
	/*======================OOP=================*/
	(upcast t.ObjectPtr from s.Name to s.BaseName) s.Depth
	= <LLPL_GenExpr (t.ObjectPtr "+" <Implode <Explode s.Name>'__'<Explode s.BaseName>>) s.Depth>;
	
	
	(mcall t.Object s.Method e.ArgsExpr) s.Depth,
    <GenExprFunctionArgs e.ArgsExpr s.Depth> : e.CompiledArgs s.NewDepth =
        e.CompiledArgs                       /*... argn ... arg1*/
	    <LLPL_GenExpr t.Object s.NewDepth>   /*... argn ... arg1 objptr*/
	    DUP                                  /*... argn ... arg1 objptr objptr*/
	    LOAD                                 /*... argn ... arg1 objptr obj_vtbl*/
	    <LLPL_GenExpr s.Method s.NewDepth>   /*... argn ... arg1 objptr obj_vtbl method*/
	    ADD                                  /*... argn ... arg1 objptr *functionPtr*/
	    LOAD                                 /*... argn ... arg1 objptr function*/
	    CALL                                 /*... */
        GETRV;                               /*... returnvalue*/
	    
}
/*Отдельная функция для генерации вызова функции. Необходимо для правильной генерации выражений-аргументов*/
GenExprFunctionArgs{
    e.ArgsTail t.ArgExpr s.Depth = 
        <LLPL_GenExpr t.ArgExpr s.Depth>
        <GenExprFunctionArgs e.ArgsTail <Add s.Depth 1>>;
    /*nothing*/ s.Depth = s.Depth;
}


/*Функция получения значения константы*/
GetConst{
	s.Name, <Cp global> : e.1 (s.Name e.Value) e.2 = Found e.Value;
	s.Name = NotFound;
}

/*Функция получения значения локальной переменной - сдвиг относительно FP*/
GetLocal{
	s.Name, <Cp local> : e.1 (s.Name e.Value) e.2 = Found e.Value;
	s.Name = NotFound;
}

/*Функция обращения объектного выражения*/
Reverse{
	t.A e.Tail = <Reverse e.Tail> t.A;
	/*empty*/ = /*empty*/;
}
```

## Тестирование

Для тестирования возможностей динамического выделения памяти мной были написаны тестовые программы
с различными структурами данных, показывающие работоспособность.

Файл `testDynMem.llpl` реализует односвязный список точек
```

(include "stdlib.llpl")
(include "DynMem.llpl")

(dynvar Point
    (refs Point_nextOne)
    (Point_x 1)
    (Point_y 1)
)


(function pointOut (point_ptr)
    (call out '(')
    (call numberOut (L ((L point_ptr) "+" Point_x)))
    (call out ' ')
    (call numberOut (L ((L point_ptr) "+" Point_y)))
    (call out ')')
)

(var test 1)

(var testStr "=" 'Hello, world!\nTest 1: single Points\n' 0)
(var test2Str "=" 'Test 2: linkedList\n' 0)
(var errorMsg "=" 'imposible to alloc. HALT!\n'  0)

(function main ()
    (var (refs One Two) (i int))
    
    (call init_heap)
    
    (call stringOut testStr)
    
    (i "=" 1)
    (while ((L i) "<" 10)
        (gc-alloc One Point)
        (if ((L One) "==" 0)
            (call stringOut errorMsg)
            (call halt 4)
        )
        (((L One) "+" Point_x) "=" (L i))
        
        (call pointOut (L One))
        (call out '\n')
        (i "++")
    )
    
    (call stringOut test2Str)
    
    (One ":-" 0)
    (Two ":-" 0)
    (i "=" 1)
    (while ((L i) "<" 10)
        (gc-alloc One Point)
        (if ((L One) "==" 0)
            (call stringOut errorMsg)
            (call halt 4)
        )
        (((L One) "+" Point_nextOne) "=" (L Two))
        (((L One) "+" Point_x) "=" (L i))
        
        (i "++")
        (Two ":-" (L One))
        
        (call pointOut (L Two))
        (call out '\n')
    )
    
    (while ((L Two) "<>" 0)
        (call pointOut (L Two))
        (call out '\n')
        
        (Two ":-" (L ((L Two) "+" Point_nextOne)))
    )
    
    (return 0)
)
```

Вывод интерпретатора при нехватки памяти(количество слов виртуальной машины равно 2000)
```
Loaded 1606 words
Hello, world!
Test 1: single Points
(1 0)
(2 0)
(3 0)
(4 0)
(5 0)
(6 0)
(7 0)
(8 0)
(9 0)
Test 2: linkedList
(1 0)
(2 0)
imposible to alloc. HALT!
1991
0 
1998
0 1998 1763 4 3 0 1870 2 
0

4
```

Вывод интерпретатора при достаточном количестве памяти(количество слов виртуальной машины равно 3000)
```
Loaded 1606 words
Hello, world!
Test 1: single Points
(1 0)
(2 0)
(3 0)
(4 0)
(5 0)
(6 0)
(7 0)
(8 0)
(9 0)
Test 2: linkedList
(1 0)
(2 0)
(3 0)
(4 0)
(5 0)
(6 0)
(7 0)
(8 0)
(9 0)
(9 0)
(8 0)
(7 0)
(6 0)
(5 0)
(4 0)
(3 0)
(2 0)
(1 0)

0
```

Файл `tree.llpl`
```
(include "DynMem.llpl")

(dynvar Tree
    (refs Tree_left Tree_right)
    
    (Tree_value int)
)

(function initTree ()
    (var (refs returnRef tmpRef leftRef rightRef))
    (comment "Для тестирования построю дерево")
    (comment 
        0 -> 1, 2
        1 -> 3 -> 4
        2 -> 5, 6
    )
    (gc-alloc leftRef Tree)
    ((leftRef "->" Tree_value) "=" 4)
    (rightRef ":-" 0)
    
    (gc-alloc returnRef Tree)
    ((returnRef "->" Tree_left) ":-" (L leftRef))
    ((returnRef "->" Tree_right) ":-" (L rightRef))
    ((returnRef "->" Tree_value) "=" 3)
    
    (leftRef ":-" (L returnRef))
    (rightRef ":-" 0)
    (gc-alloc returnRef Tree)
    ((returnRef "->" Tree_left) ":-" (L leftRef))
    ((returnRef "->" Tree_right) ":-" (L rightRef))
    ((returnRef "->" Tree_value) "=" 1)
    
    (tmpRef ":-" (L returnRef))
    
    (gc-alloc leftRef Tree)
    ((leftRef "->" Tree_value) "=" 5)
    (gc-alloc rightRef Tree)
    ((rightRef "->" Tree_value) "=" 6)
    
    (gc-alloc returnRef Tree)
    ((returnRef "->" Tree_left) ":-" (L leftRef))
    ((returnRef "->" Tree_right) ":-" (L rightRef))
    ((returnRef "->" Tree_value) "=" 2)
    
    (leftRef ":-" (L tmpRef))
    (rightRef ":-" (L returnRef))
    (gc-alloc returnRef Tree)
    ((returnRef "->" Tree_left) ":-" (L leftRef))
    ((returnRef "->" Tree_right) ":-" (L rightRef))
    ((returnRef "->" Tree_value) "=" 0)
    
    (ref-return (L returnRef))
)
(function DFS (treeRef)
    (if ((L treeRef) "==" 0)
        (return 0)
    )
    (call numberOut (L (treeRef "->" Tree_value)))
    (call out '\n')
    (call DFS (L (treeRef "->" Tree_left)))
    (call DFS (L (treeRef "->" Tree_right)))
)

(function main()
    (var (refs treeRef) (tmp int))
    
    (call init_heap)
    
    (treeRef ":-" (call initTree))
    (asm "<initptr>")
    (call DFS (L treeRef))
    (return 0)
)
```

Вывод интерпетатора файла `tree.asmbl`
```
Loaded 1587 words
0
1
3
4
2
5
6

0
```

Файл `calc.llpl`
```
(include "DynMem.llpl")

(comment Простой лексер для следующих доменов
    number ::= "[0-9]+"
    lparen ::= "("
    rparen ::= ")"
    plus ::= "+"
    star ::= "*"
    
    "================"
    <Expr> ::= <Factor> ("plus" <Factor>)*
    <Factor> ::= <Term> ("star" <Term>)*
    <Term> ::= "number" | "lparen" <Expr> "rparen"
)

(const EOF_TAG "=" 0)
(var EOF_STRING "=" 'EOF' 0)

(const NUMBER_TAG "=" 1)
(var NUMBER_STRING "=" 'NUMBER' 0)

(const LPAREN_TAG "=" 2)
(var LPAREN_STRING "=" 'LPAREN' 0)

(const RPAREN_TAG "=" 3)
(var RPAREN_STRING "=" 'RPAREN' 0)

(const PLUS_TAG "=" 4)
(var PLUS_STRING "=" 'PLUS' 0)

(const STAR_TAG "=" 5)
(var STAR_STRING "=" 'STAR' 0)

(dynvar Token 
    (refs Token_next)
    
    (Token_tag int)(Token_pos int)(Token_attr int)
)
(dynvar TokenList
    (refs TokenList_first TokenList_last)
)

(refs listOfToken)

(comment "Читает из последовательного ввода пока не встретит перевод строки")
(var errorMsg "=" 'invalid symbol ' 0)
(function tokenize()
    (var (currentChar int)(pos int)(attr int)(startpos int))
    (pos "=" 1)
    (attr "=" 0)
    
    (currentChar "=" (call in))
    (while ((L currentChar) "<>" '\n')
        (if ((L currentChar) "==" ' ')
            (currentChar "=" (call in))
            (pos "++")
        else
        (if ((L currentChar) "==" '(')
            (call addToken LPAREN_TAG (L pos) 0)
            (currentChar "=" (call in))
            (pos "++")
        else
        (if ((L currentChar) "==" ')')
            (call addToken RPAREN_TAG (L pos) 0)
            (currentChar "=" (call in))
            (pos "++")
        else
        (if ((L currentChar) "==" '+')
            (call addToken PLUS_TAG (L pos) 0)
            (currentChar "=" (call in))
            (pos "++")
        else
        (if ((L currentChar) "==" '*')
            (call addToken STAR_TAG (L pos) 0)
            (currentChar "=" (call in))
            (pos "++")
        else
        (if ((call checkDigit (L currentChar)) "==" 1)
            (startpos "=" (L pos))
            (while ((call checkDigit (L currentChar)) "==" 1)
                (attr "=" (((L attr) "*" 10) "+"
                    ((L currentChar) "-" '0')
                ))
                (currentChar "=" (call in))
                (pos "++")
            )
            (call addToken NUMBER_TAG (L startpos) (L attr))
            (attr "=" 0)
            
        else
            (call stringOut errorMsg)
            (call out '"')
            (call out (L currentChar))
            (call out '"')
            (call out '\n')
            (call halt 4)    
        ))))))
    )
    
    (call addToken EOF_TAG (L pos)(L attr))
)
(function checkDigit(character)
    (if (((L character) ">=" '0') and ((L character) "<=" '9'))
        (return 1)
    )
    (return 0)
)

(function addToken(tag position attribute)
    (var (refs last new))
    
    (gc-alloc new Token)
    (last ":-" (L (listOfToken "->" TokenList_last)))
    ((new "->" Token_next) ":-" 0)
    ((new "->" Token_tag) "=" (L tag))
    ((new "->" Token_pos) "=" (L position))
    ((new "->" Token_attr) "=" (L attribute))
    (if ((L last) "<>" 0)
        ((last "->" Token_next) "=" (L new))
    )
    ((listOfToken "->" TokenList_last) ":-" (L new))
    
    (if ((L (listOfToken "->" TokenList_first)) "==" 0)
        ((listOfToken "->" TokenList_first) "=" (L new))
    )   
)
(function printTag(tag)
    (if ((L tag) "==" EOF_TAG)
        (call stringOut EOF_STRING)
        (return 0)
    )
    (if ((L tag) "==" NUMBER_TAG)
        (call stringOut NUMBER_STRING)
        (return 0)
    )
    (if ((L tag) "==" LPAREN_TAG)
        (call stringOut LPAREN_STRING)
        (return 0)
    )
    (if ((L tag) "==" RPAREN_TAG)
        (call stringOut RPAREN_STRING)
        (return 0)
    )
    (if ((L tag) "==" PLUS_TAG)
        (call stringOut PLUS_STRING)
        (return 0)
    )
    (if ((L tag) "==" STAR_TAG)
        (call stringOut STAR_STRING)
        (return 0)
    )
    (call numberOut (L tag))
)
(function printToken(tokenRef)
    (call printTag (L (tokenRef "->" Token_tag)))
    (call out '(')
    (call numberOut (L (tokenRef "->" Token_pos)))
    (call out ')')
    (call out ':')
    (call out ' ')
    (call numberOut (L (tokenRef "->" Token_attr)))
)
(function printListOfToken()
    (var (refs iterator))
    (iterator ":-" (L (listOfToken "->" TokenList_first)))
    (while ((L iterator) "<>" 0)
        (call printToken (L iterator))
        (call out '\n')
        (iterator "=" (L (iterator "->" Token_next)))
    )
)

(comment "===============parse===================")
(comment "Парсит последовательность токенов listOfToken и выводит расчитанное значение выражения")

(refs parseIteratorRef)
(var parseErrorMsg1 "=" 'invalid token '0)
(var parseErrorMsg2 "=" ', expected '0)

(function peekToken()
    (return (L parseIteratorRef))
)
(function parseToken(tag)
    (var (refs token))
    
    (token ":-" (call peekToken))
    (if ((L(token "->" Token_tag)) "<>" (L tag))
        (call stringOut parseErrorMsg1)
        (call printToken (L token))
        (call stringOut parseErrorMsg2)
        (call printTag (L tag))
        (call out '\n')
        (call halt 5)
    )
    (parseIteratorRef ":-" (L (parseIteratorRef "->" Token_next)))
)

(function parseExpr()
    (var (refs token)(value int))
    
    (value "=" (call parseFactor))
    
    (token ":-" (call peekToken))
    (while ((L (token "->" Token_tag)) "==" PLUS_TAG)
        
        (call parseToken PLUS_TAG)
        (value "+=" (call parseFactor))
        (token ":-" (call peekToken))
    )
    
    (return (L value))
)
(function parseFactor()
    (var (value int)(tmp int))
    
    (value "=" (call parseTerm))
    (token ":-" (call peekToken))
    (while ((L (token "->" Token_tag)) "==" STAR_TAG)
        (call parseToken STAR_TAG)
        (value "*=" (call parseTerm))
        (token ":-" (call peekToken))
    )

    (return (L value))
)
(function parseTerm()
    (var (refs token)(value int))
    
    (token ":-" (call peekToken))
    (if ((L (token "->" Token_tag)) "==" NUMBER_TAG)
        (call parseToken NUMBER_TAG)
        (return (L (token "->" Token_attr)))
    else
        (call parseToken LPAREN_TAG)
        (value "=" (call parseExpr))
        (call parseToken RPAREN_TAG)
        (return (L value))
    )
)


(var separator "=" '==============\n' 0)
(var answerMsg "=" 'Result: '0)
(function main()
    (var (value int))
    (call init_heap)
    (gc-alloc listOfToken TokenList)
    
    (call tokenize)
    (call printListOfToken)
    (call stringOut separator)
    
    (parseIteratorRef ":-" (L (listOfToken "->" TokenList_first)))
    (value "=" (call parseExpr))
    (if ((L (parseIteratorRef "->" Token_tag)) "<>" EOF_TAG)
        (call stringOut parseErrorMsg1)
        (call printToken (L token))
        (call stringOut parseErrorMsg2)
        (call printTag EOF_TAG)
        (call out '\n')
        (call halt 5)
    )
    
    (call stringOut answerMsg)
    (call numberOut (L value))
    (call out '\n')
    (return 0)
    
)
```

Эта программа реализует лексический и синтаксический разбор строки простого арифметического выражения
и производит расчет его значения и выводит на экран.
Далее приведены некоторые тесты калькулятора.

Тест №1
```
Loaded 2460 words
1 + 2*3      +4
NUMBER(1): 1
PLUS(3): 0
NUMBER(5): 2
STAR(6): 0
NUMBER(7): 3
PLUS(14): 0
NUMBER(15): 4
EOF(16): 0
==============
Result: 11

0
```

Тест №2
```
Loaded 2499 words
1 + (
NUMBER(1): 1
PLUS(3): 0
LPAREN(5): 0
EOF(6): 0
==============
invalid token EOF(6): 0, expected LPAREN

5
```

Тест №3
```
Loaded 2460 words
1 + (3 + 4) * 5
NUMBER(1): 1
PLUS(3): 0
LPAREN(5): 0
NUMBER(6): 3
PLUS(8): 0
NUMBER(10): 4
RPAREN(11): 0
STAR(13): 0
NUMBER(15): 5
EOF(16): 0
==============
Result: 36

0
```


# Вывод

В ходе выполнения лабораторной работы, я реализовал в соответсвии с заданием работу с динамической памятью,
представляющую собой двусвязный список блоков, и алгоритм очистки памяти "пометить и подмести".
Также я получил навыки реализации библиотеки поддержки времени выполнения, а также отладки работы с памятью.
