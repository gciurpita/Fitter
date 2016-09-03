
# LINT_FLAG  = -Xlint:deprecation

%.class : %.java
		javac $(LINT_FLAG) $<

% : %.class
		java $@ $(Args)

Args = -D2
Pth  = pacSouth

# --------------------------------------------------------------------
fitter :  Fitter.class
		java Fitter $(Args) $(Pth)

null :    Fitter.class
		java Fitter $(Args)

sample :    Fitter.class
		java Fitter -D2 sample

# --------------------------------------------------------------------
list :
	egrep "class|public|private" Fitter.java | grep -v "\;"

.PHONY : size
size :
	wc -l .backup/20*/*/Fitter.java .backup/*/Fitter.java | grep -v total | tee $@
	c:/Tools/bin/xgraph -a $@


# --------------------------------------------------------------------
neat : 
	rm -f size *.out *xgr

clean : neat
	rm -f *~ *.class
