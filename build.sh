set -x
rm -rf tmp
mkdir tmp
export JAVA_HOME=$HOME/soft/jdk6
export JAVAC=$JAVA_HOME/bin/javac
export PATH=$PATH:$JAVA_HOME/bin
find ../../common/src/ -name '*.java' | xargs $JAVAC -d tmp
cd tmp
$JAVA_HOME/bin/jar cvf ../libs/common.jar *
cp /home/san/soft/android-sdk-linux/add-ons/addon-google_apis-google-13/libs/maps.jar ../libs/
cd ..
rm -rf tmp
rm ../ActionBarSherlock/library/gen/com/actionbarsherlock/R.java
rm ../yuku-android-util/AmbilWarna/gen/yuku/ambilwarna/R.java
rm ../com.juick.android/gen/com/juickadvanced/R.java
ant release
rm -rf libs/common.jar
