from flask_sqlalchemy import SQLAlchemy
from flask_login import UserMixin

db = SQLAlchemy()

class User(db.Model, UserMixin):
    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(150), unique=True, nullable=False)
    email = db.Column(db.String(150), unique=True, nullable=False)
    password = db.Column(db.String(150), nullable=False)
    wishlist = db.relationship('Wishlist', backref='user', lazy=True)
    expenses = db.relationship('Expense', backref='user', lazy=True, cascade="all, delete-orphan")

class Place(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(100), nullable=False)
    folder_name = db.Column(db.String(100), nullable=False)
    category = db.Column(db.String(50), default="Local Discovery")
    description = db.Column(db.Text)
    
    # Newly added fields from data.py
    history = db.Column(db.Text)
    architecture = db.Column(db.Text)
    famous_features = db.Column(db.Text)
    lat = db.Column(db.Float)
    lon = db.Column(db.Float)
    best_time = db.Column(db.String(100))
    entry_fee = db.Column(db.String(100))
    visit_duration = db.Column(db.String(100))
    city = db.Column(db.String(100))
    how_to_reach = db.Column(db.Text)
    local_tips = db.Column(db.Text)
    detailed_history = db.Column(db.Text)

class Wishlist(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    place_id = db.Column(db.Integer, db.ForeignKey('place.id'), nullable=False)
    place = db.relationship('Place')

class Expense(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    location = db.Column(db.String(100), nullable=False)
    name = db.Column(db.String(150))
    amount = db.Column(db.Float, nullable=False)
    category = db.Column(db.String(50))
    date = db.Column(db.String(50))