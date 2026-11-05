package hu.zsirkreta.app.data;

import java.time.LocalDateTime;
import java.util.List;

/** Normalizált adatok (a webes util.js norm* függvényeinek kimenete), plusz a nyers KRÉTA-válasz burka. */
public final class Models {
    private Models() {}

    public static final class Grade {
        public String id = "", subject = "", topic = "", teacher = "", text = "", kind = "", type = "", mode = "";
        public Integer value;              // 1–5, vagy null (szöveges / százalékos értékelés)
        public double weight = 1;          // 1 = 100%
        public LocalDateTime date;
        public boolean isTerm;             // félévi / év végi
    }

    public static final class Lesson {
        public String id = "", subject = "", name = "", teacher = "", sub = "", room = "", topic = "";
        public LocalDateTime start, end;
        public Integer nr;
        public boolean off, test, hw, fake;
    }

    public static final class Absence {
        public String id = "", subject = "", teacher = "", status = "", type = "", kind = "";
        public LocalDateTime date;
        public Integer nr;
        public int late;
        public boolean ok, na;
    }

    public static final class Homework {
        public String id = "", subject = "", teacher = "", text = "";
        public LocalDateTime given, due;
        public boolean done, own;
    }

    public static final class TestItem {
        public String id = "", subject = "", teacher = "", topic = "", kind = "";
        public LocalDateTime date;
        public Integer nr;
        public boolean own;
    }

    public static final class Note {
        public String id = "", title = "", text = "", teacher = "", type = "";
        public LocalDateTime date;
    }

    public static final class SubjectAvg {
        public final String subject;
        public final List<Grade> list;
        public final Double avg;
        public SubjectAvg(String subject, List<Grade> list, Double avg) { this.subject = subject; this.list = list; this.avg = avg; }
    }

    /** Saját házi (body = feladat) vagy dolgozat (body = témakör) a Tervezőből. date: ÉÉÉÉ-HH-NN */
    public static final class Plan {
        public String id, subject, date, body;
        public boolean done;
        public Plan(String id, String subject, String date, String body, boolean done) {
            this.id = id; this.subject = subject; this.date = date; this.body = body; this.done = done;
        }
    }

    /** Egy KRÉTA-válasz a gyorsítótárból vagy a hálózatról (a webes {data, meta}). */
    public static final class Raw {
        public final Object data;          // JSONArray vagy JSONObject
        public final String body;          // a nyers szöveg, a változás észleléséhez
        public final double fetchedAt;     // epoch másodperc
        public final boolean stale, cached;
        public Raw(Object data, String body, double fetchedAt, boolean stale, boolean cached) {
            this.data = data; this.body = body; this.fetchedAt = fetchedAt; this.stale = stale; this.cached = cached;
        }
    }
}
