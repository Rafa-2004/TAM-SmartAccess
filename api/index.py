from flask import Flask, jsonify

app = Flask(__name__)


@app.route("/")
def home():
    return jsonify({"projeto": "SmartAccess", "estado": "API online"})


@app.route("/api/health")
def health():
    return jsonify({"status": "ok"})
