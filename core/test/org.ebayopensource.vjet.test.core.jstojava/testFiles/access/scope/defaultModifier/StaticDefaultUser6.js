vjo.ctype('access.scope.defaultModifier.StaticDefaultUser6')
.needs('access.scope.privateModifier.PrivatePerson1')
.props({
	//> public void main(String[] args)
	main : function(args)
	{
		vjo.sysout.println(this.vj$.PrivatePerson1.getX());
		vjo.sysout.println(this.vj$.PrivatePerson1.x);
	}
})
.endType();