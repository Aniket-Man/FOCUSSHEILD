package com.example.feature.applimits.model

/**
 * Category of inspirational/discipline quote.
 */
enum class QuoteCategory(val displayName: String) {
    FOCUS("Focus"),
    DISCIPLINE("Discipline"),
    TIME("Time Value"),
    EXAMS("Exam Mastery"),
    STUDY("Deep Study"),
    CONSISTENCY("Consistency"),
    MOTIVATION("Motivation")
}

/**
 * Local offline quote data model.
 */
data class FocusQuote(
    val id: Int,
    val text: String,
    val author: String,
    val category: QuoteCategory
)

/**
 * Repository of 100+ curated local/offline quotes for the App Limit blocker and focus shields.
 */
object FocusQuotesProvider {

    private var lastIndex: Int = -1

    val quotes: List<FocusQuote> = listOf(
        // FOCUS (1-18)
        FocusQuote(1, "You don't need more time. You need fewer distractions.", "Anonymous", QuoteCategory.FOCUS),
        FocusQuote(2, "Starve your distractions, feed your focus.", "Daniel Goleman", QuoteCategory.FOCUS),
        FocusQuote(3, "Where focus goes, energy flows and results show.", "Tony Robbins", QuoteCategory.FOCUS),
        FocusQuote(4, "Deep work is the superpower of the 21st century.", "Cal Newport", QuoteCategory.FOCUS),
        FocusQuote(5, "Concentrate all your thoughts upon the work in hand. The sun's rays do not burn until brought to a focus.", "Alexander Graham Bell", QuoteCategory.FOCUS),
        FocusQuote(6, "The successful warrior is the average man, with laser-like focus.", "Bruce Lee", QuoteCategory.FOCUS),
        FocusQuote(7, "Focus is a muscle. The more you practice saying no to impulses, the stronger you become.", "James Clear", QuoteCategory.FOCUS),
        FocusQuote(8, "Multitasking is merely the ability to screw up more than one thing at a time.", "Steve Uzzell", QuoteCategory.FOCUS),
        FocusQuote(9, "Simplicity boils down to two steps: Identify the essential. Eliminate the rest.", "Leo Babauta", QuoteCategory.FOCUS),
        FocusQuote(10, "Focus on being productive instead of busy.", "Tim Ferriss", QuoteCategory.FOCUS),
        FocusQuote(11, "One hour of deep, undisturbed focus is worth ten hours of fragmented study.", "FocusShield", QuoteCategory.FOCUS),
        FocusQuote(12, "Do not let what is urgent distract you from what is important.", "Dwight D. Eisenhower", QuoteCategory.FOCUS),
        FocusQuote(13, "Your future is created by what you do today, not tomorrow.", "Robert Kiyosaki", QuoteCategory.FOCUS),
        FocusQuote(14, "Guard your attention like your most valuable possession, because it is.", "Anonymous", QuoteCategory.FOCUS),
        FocusQuote(15, "Clarity of vision creates stillness of mind.", "Marcus Aurelius", QuoteCategory.FOCUS),
        FocusQuote(16, "Attention is the rarest and purest form of generosity towards your dreams.", "Simone Weil", QuoteCategory.FOCUS),
        FocusQuote(17, "When you focus on the solution, the problems disappear.", "Anonymous", QuoteCategory.FOCUS),
        FocusQuote(18, "Eliminate the noise to hear your true potential.", "Anonymous", QuoteCategory.FOCUS),

        // DISCIPLINE (19-35)
        FocusQuote(19, "Discipline is choosing between what you want now and what you want most.", "Abraham Lincoln", QuoteCategory.DISCIPLINE),
        FocusQuote(20, "We must all suffer one of two things: the pain of discipline or the pain of regret.", "Jim Rohn", QuoteCategory.DISCIPLINE),
        FocusQuote(21, "Discipline is the bridge between goals and accomplishment.", "Jim Rohn", QuoteCategory.DISCIPLINE),
        FocusQuote(22, "Self-discipline begins with the mastery of your thoughts. If you don't control what you think, you can't control what you do.", "Napoleon Hill", QuoteCategory.DISCIPLINE),
        FocusQuote(23, "Freedom is not the absence of discipline; it is the fruit of discipline.", "Jocko Willink", QuoteCategory.DISCIPLINE),
        FocusQuote(24, "Rule your mind or it will rule you.", "Horace", QuoteCategory.DISCIPLINE),
        FocusQuote(25, "Mastering others is strength. Mastering yourself is true power.", "Lao Tzu", QuoteCategory.DISCIPLINE),
        FocusQuote(26, "Discipline is remembering what you want.", "David Campbell", QuoteCategory.DISCIPLINE),
        FocusQuote(27, "The ability to subordinate an impulse to a value is the essence of the proactive person.", "Stephen Covey", QuoteCategory.DISCIPLINE),
        FocusQuote(28, "No man is free who is not master of himself.", "Epictetus", QuoteCategory.DISCIPLINE),
        FocusQuote(29, "Small disciplines repeated with consistency every day lead to great achievements gained slowly over time.", "John C. Maxwell", QuoteCategory.DISCIPLINE),
        FocusQuote(30, "Motivation gets you going, but discipline keeps you growing.", "John C. Maxwell", QuoteCategory.DISCIPLINE),
        FocusQuote(31, "Discipline equals freedom.", "Jocko Willink", QuoteCategory.DISCIPLINE),
        FocusQuote(32, "He who conquers himself is the mightiest warrior.", "Confucius", QuoteCategory.DISCIPLINE),
        FocusQuote(33, "Your level of success is determined by your level of discipline and perseverance.", "Anonymous", QuoteCategory.DISCIPLINE),
        FocusQuote(34, "A disciplined mind brings happiness.", "Buddha", QuoteCategory.DISCIPLINE),
        FocusQuote(35, "Self-control is strength. Right thought is mastery. Calmness is power.", "James Allen", QuoteCategory.DISCIPLINE),

        // TIME (36-50)
        FocusQuote(36, "Time is what we want most, but what we use worst.", "William Penn", QuoteCategory.TIME),
        FocusQuote(37, "Lost time is never found again.", "Benjamin Franklin", QuoteCategory.TIME),
        FocusQuote(38, "The trouble is, you think you have time.", "Jack Kornfield", QuoteCategory.TIME),
        FocusQuote(39, "Time isn't the main thing. It's the only thing.", "Miles Davis", QuoteCategory.TIME),
        FocusQuote(40, "Either you run the day or the day runs you.", "Jim Rohn", QuoteCategory.TIME),
        FocusQuote(41, "Your time is limited, so don't waste it living someone else's life.", "Steve Jobs", QuoteCategory.TIME),
        FocusQuote(42, "An inch of time is an inch of gold, but you can't buy that inch of time with an inch of gold.", "Chinese Proverb", QuoteCategory.TIME),
        FocusQuote(43, "Every second you spend on mindless scrolling is a second borrowed from your future success.", "FocusShield", QuoteCategory.TIME),
        FocusQuote(44, "Dost thou love life? Then do not squander time, for that's the stuff life is made of.", "Benjamin Franklin", QuoteCategory.TIME),
        FocusQuote(45, "Time will pass anyway. You can spend it preparing or regretting.", "Anonymous", QuoteCategory.TIME),
        FocusQuote(46, "One day or Day One. You decide.", "Anonymous", QuoteCategory.TIME),
        FocusQuote(47, "Protect your time fiercely; it is the currency with which you purchase your dreams.", "Anonymous", QuoteCategory.TIME),
        FocusQuote(48, "Better three hours too soon than a minute too late.", "William Shakespeare", QuoteCategory.TIME),
        FocusQuote(49, "Yesterday is gone. Tomorrow has not yet come. We have only today. Let us begin.", "Mother Teresa", QuoteCategory.TIME),
        FocusQuote(50, "You can't make up for lost time. You can only do better in the future.", "Ashley Ormon", QuoteCategory.TIME),

        // EXAMS (51-65)
        FocusQuote(51, "Every difficult concept mastered today is one step closer to your dream college.", "FocusShield", QuoteCategory.EXAMS),
        FocusQuote(52, "The pain of studying is temporary; the pride of achieving a top rank lasts forever.", "Anonymous", QuoteCategory.EXAMS),
        FocusQuote(53, "JEE and NEET reward conceptual depth, rigorous practice, and unwavering patience.", "Exam Mentor", QuoteCategory.EXAMS),
        FocusQuote(54, "Great ranks are not made in the exam hall; they are forged in quiet daily problem solving.", "Anonymous", QuoteCategory.EXAMS),
        FocusQuote(55, "Believe in your preparation. The questions you practice today will become your confidence tomorrow.", "Anonymous", QuoteCategory.EXAMS),
        FocusQuote(56, "Success in competitive exams is a series of small, daily problems solved correctly.", "Anonymous", QuoteCategory.EXAMS),
        FocusQuote(57, "Do not fear difficult numericals. Every error analyzed is a mark gained on exam day.", "Physics Mentor", QuoteCategory.EXAMS),
        FocusQuote(58, "Hard work beats talent when talent fails to work hard.", "Tim Notke", QuoteCategory.EXAMS),
        FocusQuote(59, "Your rank will reflect the sacrifices you made when no one was watching.", "Anonymous", QuoteCategory.EXAMS),
        FocusQuote(60, "Consistent revisions turn tough formulas into automatic instincts.", "Chemistry Faculty", QuoteCategory.EXAMS),
        FocusQuote(61, "Calm mind, steady breath, sharp focus — the exam hall temperament.", "Anonymous", QuoteCategory.EXAMS),
        FocusQuote(62, "There are no shortcuts to any place worth going.", "Beverly Sills", QuoteCategory.EXAMS),
        FocusQuote(63, "The expert in anything was once a beginner who refused to quit.", "Helen Hayes", QuoteCategory.EXAMS),
        FocusQuote(64, "Champions keep playing until they get it right.", "Billie Jean King", QuoteCategory.EXAMS),
        FocusQuote(65, "Every mock test is a mirror; improve the reflection every single week.", "Math Faculty", QuoteCategory.EXAMS),

        // STUDY & DEEP WORK (66-80)
        FocusQuote(66, "Study hard in silence; let your success make the noise.", "Frank Ocean", QuoteCategory.STUDY),
        FocusQuote(67, "An investment in knowledge pays the best interest.", "Benjamin Franklin", QuoteCategory.STUDY),
        FocusQuote(68, "Live as if you were to die tomorrow. Learn as if you were to live forever.", "Mahatma Gandhi", QuoteCategory.STUDY),
        FocusQuote(69, "The beautiful thing about learning is that no one can take it away from you.", "B.B. King", QuoteCategory.STUDY),
        FocusQuote(70, "Deep understanding requires uninterrupted contemplation.", "Albert Einstein", QuoteCategory.STUDY),
        FocusQuote(71, "Anyone who stops learning is old, whether at twenty or eighty.", "Henry Ford", QuoteCategory.STUDY),
        FocusQuote(72, "Knowledge is having the right answer. Intelligence is asking the right question.", "Anonymous", QuoteCategory.STUDY),
        FocusQuote(73, "It is what you read when you don't have to that determines what you will be when you can't help it.", "Oscar Wilde", QuoteCategory.STUDY),
        FocusQuote(74, "Develop a passion for learning. If you do, you will never cease to grow.", "Anthony J. D'Angelo", QuoteCategory.STUDY),
        FocusQuote(75, "Study while others are sleeping; work while others are loafing; prepare while others are playing.", "William Arthur Ward", QuoteCategory.STUDY),
        FocusQuote(76, "Education is the passport to the future, for tomorrow belongs to those who prepare for it today.", "Malcolm X", QuoteCategory.STUDY),
        FocusQuote(77, "The roots of education are bitter, but the fruit is sweet.", "Aristotle", QuoteCategory.STUDY),
        FocusQuote(78, "To acquire knowledge, one must study; but to acquire wisdom, one must observe.", "Marilyn vos Savant", QuoteCategory.STUDY),
        FocusQuote(79, "Learning is not attained by chance; it must be sought for with ardor and attended to with diligence.", "Abigail Adams", QuoteCategory.STUDY),
        FocusQuote(80, "The more that you read, the more things you will know. The more that you learn, the more places you'll go.", "Dr. Seuss", QuoteCategory.STUDY),

        // CONSISTENCY (81-95)
        FocusQuote(81, "Consistency is what transforms average into excellence.", "Tony Robbins", QuoteCategory.CONSISTENCY),
        FocusQuote(82, "It's not what we do once in a while that shapes our lives. It's what we do consistently.", "Tony Robbins", QuoteCategory.CONSISTENCY),
        FocusQuote(83, "Success doesn't come from what you do occasionally. It comes from what you do consistently.", "Marie Forleo", QuoteCategory.CONSISTENCY),
        FocusQuote(84, "A river cuts through rock not because of its power, but because of its persistence.", "Jim Watkins", QuoteCategory.CONSISTENCY),
        FocusQuote(85, "We are what we repeatedly do. Excellence, then, is not an act, but a habit.", "Will Durant", QuoteCategory.CONSISTENCY),
        FocusQuote(86, "Small daily improvements over time lead to stunning results.", "Robin Sharma", QuoteCategory.CONSISTENCY),
        FocusQuote(87, "Do not despise small beginnings. Daily 3-hour study blocks build monumental achievements.", "FocusShield", QuoteCategory.CONSISTENCY),
        FocusQuote(88, "Drip by drip the water fills the pot. Likewise, the wise person, gathering it little by little, fills oneself with good.", "Buddha", QuoteCategory.CONSISTENCY),
        FocusQuote(89, "Energy and persistence conquer all things.", "Benjamin Franklin", QuoteCategory.CONSISTENCY),
        FocusQuote(90, "Patience, persistence and perspiration make an unbeatable combination for success.", "Napoleon Hill", QuoteCategory.CONSISTENCY),
        FocusQuote(91, "The secret of your future is hidden in your daily routine.", "Mike Murdock", QuoteCategory.CONSISTENCY),
        FocusQuote(92, "Perseverance is the hard work you do after you get tired of doing the hard work you already did.", "Newt Gingrich", QuoteCategory.CONSISTENCY),
        FocusQuote(93, "Great works are performed not by strength, but by perseverance.", "Samuel Johnson", QuoteCategory.CONSISTENCY),
        FocusQuote(94, "Never give up on a dream just because of the time it will take to accomplish it. The time will pass anyway.", "Earl Nightingale", QuoteCategory.CONSISTENCY),
        FocusQuote(95, "Step by step, the longest march is completed.", "Anonymous", QuoteCategory.CONSISTENCY),

        // MOTIVATION & COURAGE (96-110)
        FocusQuote(96, "The secret of getting ahead is getting started.", "Mark Twain", QuoteCategory.MOTIVATION),
        FocusQuote(97, "It always seems impossible until it's done.", "Nelson Mandela", QuoteCategory.MOTIVATION),
        FocusQuote(98, "Don't watch the clock; do what it does. Keep going.", "Sam Levenson", QuoteCategory.MOTIVATION),
        FocusQuote(99, "Act as if what you do makes a difference. It does.", "William James", QuoteCategory.MOTIVATION),
        FocusQuote(100, "Believe you can and you're halfway there.", "Theodore Roosevelt", QuoteCategory.MOTIVATION),
        FocusQuote(101, "Your only limit is the amount of dedication you are willing to give.", "FocusShield", QuoteCategory.MOTIVATION),
        FocusQuote(102, "Start where you are. Use what you have. Do what you can.", "Arthur Ashe", QuoteCategory.MOTIVATION),
        FocusQuote(103, "Aim for the moon. If you miss, you may hit a star.", "W. Clement Stone", QuoteCategory.MOTIVATION),
        FocusQuote(104, "Everything you've ever wanted is on the other side of fear and distraction.", "George Addair", QuoteCategory.MOTIVATION),
        FocusQuote(105, "Courage doesn't always roar. Sometimes courage is the quiet voice at the end of the day saying, 'I will try again tomorrow.'", "Mary Anne Radmacher", QuoteCategory.MOTIVATION),
        FocusQuote(106, "Press on. Nothing in the world can take the place of persistence.", "Calvin Coolidge", QuoteCategory.MOTIVATION),
        FocusQuote(107, "Fall seven times, stand up eight.", "Japanese Proverb", QuoteCategory.MOTIVATION),
        FocusQuote(108, "What lies behind us and what lies before us are tiny matters compared to what lies within us.", "Ralph Waldo Emerson", QuoteCategory.MOTIVATION),
        FocusQuote(109, "You are never too old to set another goal or to dream a new dream.", "C.S. Lewis", QuoteCategory.MOTIVATION),
        FocusQuote(110, "Turn your wounds into wisdom and your hours into mastery.", "Oprah Winfrey", QuoteCategory.MOTIVATION)
    )

    /**
     * Retrieves a quote without immediate repetition.
     */
    @Synchronized
    fun getNextQuote(seed: Long = System.currentTimeMillis()): FocusQuote {
        if (quotes.isEmpty()) {
            return FocusQuote(0, "Protect your time. Protect your future.", "FocusShield", QuoteCategory.FOCUS)
        }
        val size = quotes.size
        var nextIdx = ((seed % size).toInt() + (0..size).random()) % size
        if (nextIdx == lastIndex) {
            nextIdx = (nextIdx + 1) % size
        }
        lastIndex = nextIdx
        return quotes[nextIdx]
    }
}
