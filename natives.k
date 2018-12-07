* Базовые правила для родственных отношений

!@x $y parent(y,x);
* У всех есть отцы

!@x ~parent(x,x);
* Никто не может быть собственным отцом.

!@x (male(x) || female(x)) && ~(male(x) && female(x));
* Каждый является мужчиной или женщиной.

!@x @y parent(x,y) ->
    child(y,x),
    (male(x) -> father(x,y)),
    (female(x) -> mother(x,y));
!@x @y (father(x,y) -> male(x), parent(x,y)), (mother(x,y) -> femaile(x), parent(x,y));

!@x @y child(x,y) ->
    parent(y,x);

!@x @y daughter(x,y) -> female(x), child(x,y);
!@x @y son(x,y) -> male(x), child(x,y);
* Дочь всегда женского пола, сын – мужского.

* База данных. Утверждения.

!father(John, Tom);
!daughter(Sarah, John);

* Возраст

!age(John, 37);
!age(Tom, 12);
!age(Sarah, 4);

