!@x ~move(x, x);
!@x @y @z move(x, y), move(y, z), x != z -> move(x, z);
//!@x @y move(x, y), x != y -> move(y, x);

!@x @y move(x,y), x != y -> route(x,y,1);
!@x @y @z @n1 @n2 route(x,y,n1), route(y,z,n2), x != z -> route(x,z,n1 + n2);

!move(A,B);
!move(B,C);
!move(C,D);
!move(D,E);

!move(A,F);
!move(F,G);
!move(G,E);

//!move(A,E);


