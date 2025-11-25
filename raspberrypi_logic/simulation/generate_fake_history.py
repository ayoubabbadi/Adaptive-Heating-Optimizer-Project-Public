import json
import random
import os
from datetime import datetime, timedelta

DAYS_BACK = 30
# Ensures the file is generated in the same directory as the script
script_dir = os.path.dirname(os.path.abspath(__file__))
FILENAME = os.path.join(script_dir, "motion_history.json")

history_data = []

start_date = datetime.now() - timedelta(days=DAYS_BACK)

for i in range(DAYS_BACK):
    current_day = start_date + timedelta(days=i)
    weekday = current_day.weekday()

    for hour in range(24):
        is_active = False

        if 0 <= weekday <= 4:
            if 7 <= hour <= 8:
                if random.random() > 0.15:
                    is_active = True
            elif 9 <= hour <= 17:
                if random.random() > 0.95:
                    is_active = True
            elif 18 <= hour <= 23:
                if random.random() > 0.1:
                    is_active = True
        else:
            if 10 <= hour <= 23:
                if random.random() > 0.2:
                    is_active = True

        if is_active:
            for _ in range(random.randint(1, 4)):
                minute = random.randint(0, 59)
                event_time = current_day.replace(hour=hour, minute=minute, second=0, microsecond=0)

                record = {
                    "timestamp": event_time.strftime("%Y-%m-%d %H:%M:%S"),
                    "event": "MOTION_DETECTED"
                }
                history_data.append(record)

try:
    with open(FILENAME, 'w') as f:
        json.dump(history_data, f, indent=2)
    print(f"History generated at: {FILENAME}")
except IOError as e:
    print(f"Error writing file: {e}")
