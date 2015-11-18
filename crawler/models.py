# ASU CSE 591
# Author: Group 4

import sqlalchemy as sql
import sqlalchemy.orm as orm
import sqlalchemy.sql.expression as sqlexpr
import sqlalchemy.dialects.postgresql as psql
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()

class User(Base):
    __tablename__ = 'users'

    id = sql.Column(sql.BigInteger, primary_key=True)
    next_max_id = sql.Column(sql.String)
    fully_scraped = sql.Column(sql.Boolean, default=False)
    private = sql.Column(sql.Boolean, default=False)

    def __repr__(self):
        return "<User(id={0}, next_max_id='{1}' fully_scraped={2}, private={3})>".format(
        	self.id, self.next_max_id, self.fully_scraped, self.private)

class Location(Base):
    __tablename__ = 'locations'

    id = sql.Column(sql.BigInteger, primary_key=True)
    name = sql.Column(sql.String)
    latitude = sql.Column(sql.Float)
    longitude = sql.Column(sql.Float)

    def __repr__(self):
        return "<Location(id={0}, name='{1}', latitude={2}, longitude={3})>".format(
            self.id, self.name, self.latitude, self.longitude)


class Media(Base):
    __tablename__ = 'media'

    id = sql.Column(sql.BigInteger, primary_key=True)
    date = sql.Column(sql.DateTime)
    caption = sql.Column(sql.String)
    tags = sql.Column(psql.ARRAY(sql.String))
    type = sql.Column(sql.String)

    user_id = sql.Column(sql.BigInteger, sql.ForeignKey('users.id'))
    user = orm.relationship('User', backref=orm.backref('media'))
    location_id = sql.Column(sql.BigInteger, sql.ForeignKey('locations.id'))
    location = orm.relationship('Location', backref=orm.backref('media'))

    def __repr__(self):
        return "<Image(id={0}, date='{1}', tags={2}, type='{3}', user={4}, location={5}>".format(
            self.id, self.date, self.tags, self.is_image, self.user, self.location)

def create_engine(connstring):
	return sql.create_engine(connstring)

def create_session(engine):
	return orm.sessionmaker(bind=engine)()

def create_tables(engine):
	Base.metadata.create_all(engine)

def random_users(session, num):
	# Postgres specific
    return session.query(User).order_by(sqlexpr.func.random()).limit(num).all()
