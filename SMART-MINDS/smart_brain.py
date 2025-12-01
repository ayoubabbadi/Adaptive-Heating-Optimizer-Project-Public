import json
import os
from datetime import datetime
from collections import defaultdict

class SmartHabitTracker:
    def __init__(self, history_file="motion_history.json"):
        self.filename = history_file
        self.probability_grid = {}
        self.active_threshold = 0.4
        self.last_analysis = None

        self.analyze_habits()

    def add_realtime_event(self):
        new_record = {
            "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "event": "MOTION_DETECTED"
        }

        data = self._load_data()
        data.append(new_record)
        self._save_data(data)

    def analyze_habits(self):
        data = self._load_data()
        if not data:
            return

        counts = defaultdict(lambda: defaultdict(set))

        first_date = None
        last_date = None

        for record in data:
            try:
                dt = datetime.strptime(record['timestamp'], "%Y-%m-%d %H:%M:%S")
                if not first_date: first_date = dt
                last_date = dt

                day_name = dt.strftime("%A")
                hour = dt.hour
                date_str = dt.strftime("%Y-%m-%d")

                counts[day_name][hour].add(date_str)
            except ValueError:
                continue

        if first_date and last_date:
            delta = last_date - first_date
            total_weeks = max(delta.days / 7.0, 1.0)
        else:
            total_weeks = 1.0

        self.probability_grid = defaultdict(dict)

        for day in ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]:
            for h in range(24):
                unique_days_active = len(counts[day][h])
                score = unique_days_active / total_weeks
                self.probability_grid[day][h] = min(score, 1.0)

        self.last_analysis = datetime.now()

    def check_habit_status(self):
        now = datetime.now()
        day_name = now.strftime("%A")
        hour = now.hour

        score = self.probability_grid.get(day_name, {}).get(hour, 0.0)
        is_habit = score >= self.active_threshold

        status_msg = f"{day_name} {hour}:00 ({int(score*100)}% Probability)"
        return is_habit, score, status_msg

    def _load_data(self):
        if not os.path.exists(self.filename):
            return []
        try:
            with open(self.filename, 'r') as f:
                return json.load(f)
        except:
            return []

    def _save_data(self, data):
        with open(self.filename, 'w') as f:
            json.dump(data, f, indent=2)
