
* Базовые правила для родственных отношений

!@x $y father(y,x);
* У всех есть отцы

!@x ~father(x,x);
* Никто не может быть собственным отцом.

!@x (male(x) || female(x)), ~(male(x) &&  female(x));
* Каждый является мужчиной или женщиной.

!@x ($y daughter(x,y)) -> female(x), child(x,y);
!@x ($y son(x,y)) -> male(x), child(x,y);
* Дочь всегда женского пола, сын – мужского.

!@x @y father(x,y) -> male(x), child(y,x);
* Если x отец y, то x мужского пола, y является
* потомком x. 

!@x @y child(y,x) -> 
	(male(y) -> son(y,x)),
	(female(y) -> daughter(y,x));
* Если y потомок x, то если у мужского пола,
* то он сын x, если женского – то дочь.

!@x @y child(x,y) -> 
	(male(y) -> father(y,x)),
	(female(y) -> mother(y,x));
* Если x потомок y, то если y женского пола, то он мать,
* если мужского – отец.

* База данных. Утверждения.

!father(John, Tom);
!daughter(Sarah, John);

* Возраст

!age(John, 37);
!age(Tom, 12);
!age(Sarah, 4);





