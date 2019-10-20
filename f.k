=plus(a,b) 
	{plus = a+b;},
	{a = plus-b;},
	{b = plus-a;};

=plus(a,b,c) 
	{plus = a+b+c;},
	{a = plus-b-c;},
	{b = plus-a-c;},
	{c = plus-a-b;};

=ask(a)	{
	switch(a) {
	case 1 : 
		ask = "Это один";
		break;
	case 2 : 
		ask = "Это два";
		break;
	case 3 : 
		ask = "Это три";
		break;
	case 4 : 
		ask = "Это тшэтыри";
		break;
	default : 
		ask = "Фиг знает";
		break;
	}
};