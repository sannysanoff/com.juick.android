rm -rf tmp
mkdir tmp
export JAVAC=$HOME/soft/jdk1.6.0_18/bin/javac
find ../../common/src/ -name '*.java' | xargs $JAVAC -d tmp
cd tmp
jar cvf ../libs/common.jar *
cd ..
rm -rf tmp
ant release
rm -rf libs/common.jar
