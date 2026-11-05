package ru.otlipni.app;

import android.content.Context;

/** Тексты уведомлений на языке, выбранном в игре. */
public class Tx {
    static boolean en(Context c) {
        String l = Checks.sp(c).getString("lang", "");
        if (l.isEmpty()) l = java.util.Locale.getDefault().getLanguage();
        return !(l.equals("ru") || l.equals("uk") || l.equals("be") || l.equals("kk"));
    }
    static String pet(Context c) { return en(c) ? "Pet" : "Питомец"; }
    static String chStatus(Context c) { return en(c) ? "Is your pet asleep" : "Питомец спит или нет"; }
    static String chPet(Context c) { return en(c) ? "Pet" : "Питомец"; }
    static String sleeping(Context c, String n) { return en(c) ? n + " is asleep" : n + " спит"; }
    static String sleepingText(Context c) { return en(c) ? "Don't unlock your phone, let it rest" : "Не разблокируй телефон — пусть выспится"; }
    static String awake(Context c, String n) { return en(c) ? n + " is awake" : n + " не спит"; }
    static String awakeText(Context c, long last) {
        if (en(c)) return last >= 60 ? "Last nap: " + fmt(c, last) + ". Lock your phone and it falls asleep again." : "Lock your phone and it falls asleep";
        return last >= 60 ? "Последний сон: " + fmt(c, last) + ". Заблокируй телефон — уснёт снова." : "Заблокируй телефон — и он уснёт";
    }
    static String screenTitle(Context c, long s) { return en(c) ? "You've been on your phone for " + fmt(c, s) : "Ты в телефоне уже " + fmt(c, s); }
    static String screenText(Context c, String n) { return en(c) ? n + " goes hungry while your screen is unlocked. Lock your phone for a bit." : n + " голодает, пока экран разблокирован. Заблокируй телефон хоть ненадолго."; }
    static String sleptTitle(Context c, String n, long s) { return en(c) ? n + " slept " + fmt(c, s) : n + " проспал " + fmt(c, s); }
    static String sleptText(Context c) { return en(c) ? "While your phone was down it had dreams and seems to have found something. Take a look." : "Пока телефон лежал, он видел сны и, кажется, что-то нашёл. Загляни."; }
    static String share(Context c) { return en(c) ? "Share" : "Поделиться"; }
    static String fmt(Context c, long s) {
        long m = s / 60;
        if (en(c)) return m < 60 ? m + " min" : (m / 60) + " h " + (m % 60) + " min";
        return m < 60 ? m + " мин" : (m / 60) + " ч " + (m % 60) + " мин";
    }
}
