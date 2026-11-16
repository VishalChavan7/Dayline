package com.dayline.app.core.domain

import java.time.LocalDate

data class Quote(val text: String, val author: String)

/** A small offline collection of classic, public-domain quotes; one per day, stable for the whole day. */
object Quotes {
    val all = listOf(
        Quote("Well begun is half done.", "Aristotle"),
        Quote("It does not matter how slowly you go as long as you do not stop.", "Confucius"),
        Quote("Lost time is never found again.", "Benjamin Franklin"),
        Quote("While we are postponing, life speeds by.", "Seneca"),
        Quote("A journey of a thousand miles begins with a single step.", "Lao Tzu"),
        Quote("Do what you can, with what you have, where you are.", "Theodore Roosevelt"),
        Quote("Well done is better than well said.", "Benjamin Franklin"),
        Quote("Begin at once to live.", "Seneca"),
        Quote("Quality is not an act, it is a habit.", "Aristotle"),
        Quote("The secret of getting ahead is getting started.", "Mark Twain"),
        Quote("Simplicity is the ultimate sophistication.", "Leonardo da Vinci"),
        Quote("He who has a why to live can bear almost any how.", "Friedrich Nietzsche"),
        Quote("Knowing is not enough; we must apply.", "Johann Wolfgang von Goethe"),
        Quote("Energy and persistence conquer all things.", "Benjamin Franklin"),
        Quote("First say to yourself what you would be; then do what you have to do.", "Epictetus"),
        Quote("You may delay, but time will not.", "Benjamin Franklin"),
        Quote("Waste no more time arguing what a good man should be. Be one.", "Marcus Aurelius"),
        Quote("The impediment to action advances action.", "Marcus Aurelius"),
        Quote("What we fear doing most is usually what we most need to do.", "Ralph Waldo Emerson"),
        Quote("Patience and perseverance have a magical effect.", "John Quincy Adams"),
        Quote("Luck is what happens when preparation meets opportunity.", "Seneca"),
    )

    fun forDay(date: LocalDate): Quote = all[(date.toEpochDay() % all.size).toInt().let { if (it < 0) it + all.size else it }]
}
