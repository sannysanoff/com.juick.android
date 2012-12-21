set -x
rm -rf tmp
mkdir tmp
find .. -name 'bin' -type d| xargs rm -rf
export JAVA_HOME=$HOME/soft/jdk6
export JAVAC=$JAVA_HOME/bin/javac
export PATH=$PATH:$JAVA_HOME/bin
find ../../common/src/ -name '*.java' | xargs $JAVAC -d tmp
cd tmp
$JAVA_HOME/bin/jar cvf ../libs/common.jar *
cp /home/san/soft/android-sdk-linux/add-ons/addon-google_apis-google-13/libs/maps.jar ../libs/
cd ..
rm -rf tmp
find .. -name 'R.java' | xargs rm
ant release
rm -rf libs/common.jar
