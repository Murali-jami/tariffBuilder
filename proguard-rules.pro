-dontshrink
-dontoptimize
-ignorewarnings

-keepattributes *Annotation*

-keep public class com.xius.TariffBuilder.TariffBuilderApplication {
    public static void main(java.lang.String[]);
}

-keep @org.springframework.web.bind.annotation.RestController class *
-keep @org.springframework.stereotype.Service class *
-keep @org.springframework.stereotype.Repository class *
-keep @org.springframework.context.annotation.Configuration class *