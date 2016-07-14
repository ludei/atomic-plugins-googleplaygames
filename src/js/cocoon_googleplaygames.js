(function() {

    if (window.cordova && typeof require !== 'undefined') {
        require('cocoon-plugin-social-common.Social'); //force dependency load
    }
    var Cocoon = window.Cocoon;

    /**
    * @fileOverview
    <h2>About Atomic Plugins</h2>
    <p>Atomic Plugins provide an elegant and minimalist API and are designed with portability in mind from the beginning. Framework dependencies are avoided by design so the plugins can run on any platform and can be integrated with any app framework or game engine.
    <br/><p>You can contribute and help to create more awesome plugins. </p>
    <h2>Atomic Plugins for Google Play Games</h2>
    <p>This repo contains Google Play Games APIs designed using the Atomic Plugins paradigm. Connect your app to Google Play Games and take advantage of all the features provided. The API is already available in many languagues and we have the plan to add more in the future.</p>
    <h3>Setup your project</h3>
    <p>Releases are deployed to NPM.
    You only have to install the desired plugins using Cordova CLI and <a href="https://cocoon.io"/>Cocoon Cloud service</a>.</p>
    <ul>
    <code>
    cordova plugin add cocoon-plugin-social-android-googleplaygames<br/>
    </code>
    </ul>
    <h3>Documentation</h3>
    <p>In this section you will find all the documentation you need for using this plugin in your Cordova project.
    Select the specific namespace below to open the relevant documentation section:</p>
    <ul>
    <li><a href="http://ludei.github.io/atomic-plugins-docs/dist/doc/js/Cocoon.Social.GooglePlayGames.html">Google Play Games</a></li>
    </ul>
    * @version 1.0
    */

    /**
     * Cocoon Social Interface for the Google Play Games Extension.
     * @namespace Cocoon.Social.GooglePlayGames
     */

    Cocoon.define("Cocoon.Social", function(extension) {


        extension.GooglePlayGamesExtension = function() {

            this.session = null;
            this.serviceName = "LDGooglePlayGamesPlugin";
            this.onSessionChanged = new Cocoon.Signal();
            this.on = this.onSessionChanged.expose();
            this.defaultScopes = ["https://www.googleapis.com/auth/games","https://www.googleapis.com/auth/plus.login"];
            return this;
        };

        extension.GooglePlayGamesExtension.prototype = {
            settings: {},
            socialService: null,
            initialized: false,

            auth: null,
            client: null,

            /**
             * Initializes the service and tries to restore the last session.
             * @memberof Cocoon.Social.GooglePlayGames
             * @function init
             * @param {Object} params Initialization options.
             * @param {string} params.clientId The application clientID. Omit if its already provided in the native application via cloud configuration.
             * @param {string} params.defaultLeaderboard The default leaderboard ID. You can omit it if you specify the leaderboardID in all the score queries or submits.
             * @param {array} params.scopes Additional scope identifiers. Plus & Games APIs are included by default. To use cloud saved game include the 'drive.appfolder' scope.
             * @param {boolean} params.showAchievementNotifications Enables or disables the native view notifications when an achievement is unlocked.
             * @param {function} callback The initialization completed callback. Received params: error
             */
            init: function(params, callback) {
                if (!params || typeof params !== 'object')
                    throw "Invalid params argument";
                this.settings = params;
                if (!this.settings.hasOwnProperty('showAchievementNotifications')) {
                    this.settings.showAchievementNotifications = true;
                }
                this.initialized = true;
                var me = this;

                Cocoon.exec(this.serviceName, "setListener", [], function(data) {
                    me.session = data.session;
                    me.onSessionChanged.emit("sessionChanged",null, [data]);
                });

                Cocoon.exec(this.serviceName, "init", [me.settings], function(error){
                    if (callback) {
                        callback(error);
                    }
                });

            },

            /**
             * Returns a Cocoon SocialGaming interface for the Google Play Games Extension.
             * You can use the Google Play Games extension in two ways, with the official SDK API equivalent or with the CocoonJS Social API abstraction.
             * @memberof Cocoon.Social.GooglePlayGames
             * @function getSocialInterface
             * @see Cocoon.Social.Interface
             * @returns {Cocoon.Social.Interface}
             */
            getSocialInterface: function() {

                if (!this.initialized) {
                    throw "You must call init() before getting the Social Interface";
                }
                if (!this.socialService) {
                    this.socialService = new Cocoon.Social.SocialServiceGooglePlayGames(this);
                }
                return this.socialService;
            },

            /**
             * Returns a Cocoon Multiplayer interface for the Game Center Extension.
             * @memberof Cocoon.Social.GooglePlayGames
             * @function getMultiplayerInterface
             * @returns {Cocoon.Multiplayer.MultiplayerService}
             */
            getMultiplayerInterface: function() {
                return Cocoon.Multiplayer.GooglePlayGames;
            },

             /**
             * Authenticates the user.
             * @function login
             * @memberOf Cocoon.Social.GooglePlayGames
             * @param {object} param login params
             * @param {function} callback The callback function. Response params: session and error.
             */
            login: function(params, callback) {
                var me = this;
                Cocoon.exec(this.serviceName, "login", [params], function(response) {
                    me.session = response.session;
                    if (callback) {
                        callback(response.session, response.error);
                    }
                }, function(response) {
                    me.session = response.session;
                    if (callback) {
                        callback(response.session, response.error);
                    }
                });
            },

            isLoggedIn: function() {
                return !!(this.session && this.session.playerId);
            },

            /**
             * Synchronous accessor for the current session.
             * @function getSession
             * @memberOf Cocoon.Social.GooglePlayGames
             * @returns {Object} Current session data.
             */
            getSession: function() {
                return this.session;
            },

            /**
             * Logs the user out of the application
             * @param callback
             */
            disconnect: function(callback) {
                Cocoon.exec(this.serviceName, "disconnect", [], callback, callback);
            },

            /**
             * Submit event
             * @memberof Cocoon.Social.GooglePlayGames
             * @function submitEvent
             * @param {string} eventId The event Id.
             * @param {number} increment The amount the event will be incremented with.
             */
            submitEvent: function(eventId, increment) {
                Cocoon.exec(this.serviceName, "submitEvent", [eventId, increment], null, null);
            },

            /**
             * Loads a Saved Game Snapshot from the cloud
             * @memberof Cocoon.Social.GooglePlayGames
             * @function loadSavedGame
             * @param {string} identifier snapshot identifier
             * @param {Function} callback The callback function. It receives the following parameters:
             * - Snapshot: Object with the title, description adn identifier of the snapshort. The data property contains the raw bytes converted to string.
             * - Error.
             */
            loadSavedGame: function(identifier, callback) {
                 Cocoon.exec(this.serviceName, "loadSavedGame", [identifier], callback, callback);
             },

            /**
             * Writes a SavedGame Snapshot to the cloud
             * @memberof Cocoon.Social.GooglePlayGames
             * @function writeSavedGame
             * @param {Object} snapshot snapshot object to store. New snapshot is created if the identifier is a new one. An object with:
             * - Identifier
             * - Title
             * - Description
             * - Data properties.
             * @param {Function} callback The callback function. It receives the following parameters:
             * - Error.
             */
            writeSavedGame: function(snapshot, callback) {
                  Cocoon.exec(this.serviceName, "writeSavedGame", [snapshot], callback, callback);
             },

            /**
             * Show Google Play saved games activity and allows the user to choose a saved game or to create a new one
             * @memberof Cocoon.Social.GooglePlayGames
             * @function showSavedGames
             * @param {Function} callback The callback function. It receives the following parameters:
             * - Snapshot: The selected snapshot metada, use loadSavedGame to fetch its data. If the user creates a new snapshot the idenfier will be empty.
             * - Error.
             */
            showSavedGames: function(callback) {
                Cocoon.exec(this.serviceName, "showSavedGames", [], callback, callback);
             },

             /**
              * Loads the earned achievements for the current logged in user.
              * @function loadAchievements
              * @memberOf Cocoon.Social.GooglePlayGames
              * @param {function} callback The callback function. It receives the following parameters:
              * - Array of {@link Cocoon.Social.GooglePlayGames.Achievement}.
              * - Error.
              */
             loadAchievements: function(callback) {
                 Cocoon.exec(this.serviceName, "loadAchievements", [], function(achievements) {
                     callback(achievements || [], null);
                 }, function(error) {
                     callback(null, error);
                 });
             },

            /**
             * Reports user earned achievements to the server.
             * @function submitAchievements
             * @memberOf Cocoon.Social.GooglePlayGames
             * @param {string} achievementID
             * @param {boolean} showNotfication if true it shows a native achievement earned animation
             * @param {function} callback The callback function. Response params: error.
             */
            submitAchievement: function(achievementID, showNotification, callback) {
                Cocoon.exec(this.serviceName, "submitAchievement", [achievementID, !!showNotification], function() {
                    callback();
                }, function(error) {
                    callback(error);
                });
            },


            /**
             * Loads the current player score for a specifier leaderboardID.
             * @function loadScore
             * @memberOf Cocoon.Social.GooglePlayGames
             * @param {string} leaderboardID The leaderboard identifier to get score from.
             * @param {function} callback The callback function. It receives the following parameters:
             * - Score: number
             * - Error.
             */
            loadScore: function(leaderboardID, callback) {
                Cocoon.exec(this.serviceName, "loadScore", [leaderboardID], function(score) {
                    callback(score, null);
                }, function(error) {
                    callback(0, error);
                });
            },

            /**
             * Report user score to the server.
             * @function submitScore
             * @memberOf Cocoon.Social.GooglePlayGames
             * @param {number} score The score to submit
             * @param {string} leaderboardID The leaderboard identifier
             * @param {function} callback The callback function. Response params: error.
             */
            submitScore: function(score, leaderboardID, callback) {
                Cocoon.exec(this.serviceName, "submitScore", [score, leaderboardID], function() {
                    callback();
                }, function(error) {
                    callback(error);
                });
            },

            /**
             * Sums a score to the existing score value in the specified leaderboardID
             * @function submitScore
             * @memberOf Cocoon.Social.GooglePlayGames
             * @param {number} score The score to submit
             * @param {string} leaderboardID The leaderboard identifier
             * @param {function} callback The callback function. Response params: error.
             */
            addScore: function(score, leaderboardID, callback) {
                Cocoon.exec(this.serviceName, "addScore", [score, leaderboardID], function() {
                    callback();
                }, function(error) {
                    callback(error);
                });
            },

            /**
             * Shows a native view with the standard user interface for achievements.
             * @function showAchievements
             * @memberOf Cocoon.Social.GooglePlayGames
             * @param {function} callback The callback function when the view is closed by the user. Response params: error
             */
            showAchievements: function(callback) {
                Cocoon.exec(this.serviceName, "showAchievements", [], function() {
                    if (callback) {callback();}
                }, function(error) {
                    if (callback){callback(error);}
                });
            },

            /**
             * Shows a native view with the standard user interface for leaderboards.
             * @function showLeaderboard
             * @memberOf Cocoon.Social.GooglePlayGames
             * @param {function} callback The callback function when the view is closed by the user. Response params: error
             * @param {string} [leaderboardID] Optional leaderboard id. If not specified the leaderboard list is shown
             */
            showLeaderboard: function(callback, leaderboardID) {
                Cocoon.exec(this.serviceName, "showLeaderboard", [leaderboardID], function() {
                    if (callback) {
                        callback();
                    }
                }, function(error) {
                    if (callback){
                        callback(error);
                    }
                });
            },

            /**
             * Loads the player data for the specified playerId.
             * @function loadPlayer
             * @memberOf Cocoon.Social.GooglePlayGames
             * @param {string} playerId the player identifier to load the data from
             * @param {function} callback The callback function. It receives the following parameters:
             * - {@link Cocoon.Social.GooglePlayGames.Player} The player data
             * - Error.
             */
            loadPlayer: function(playerId, callback) {
                Cocoon.exec(this.serviceName, "loadPlayer", [playerId || ""], function(player) {
                    callback(player, null);
                }, function(error) {
                    callback(null, error);
                });
            },

        };

        extension.GooglePlayGames = new extension.GooglePlayGamesExtension();


        extension.SocialServiceGooglePlayGames = function (apiObject) {
            Cocoon.Social.SocialServiceGooglePlayGames.superclass.constructor.call(this);
            this.gapi = apiObject;
            var me = this;

            this.gapi.on('sessionChanged',function(data){
                me.onLoginStatusChanged.emit("loginStatusChanged",null,[me.gapi.isLoggedIn(), data.error]);
            });

            return this;
        };

        extension.SocialServiceGooglePlayGames.prototype =  {

            isLoggedIn: function() {
                return this.gapi.isLoggedIn();
            },
            login : function(callback) {
                var me = this;
                this.gapi.login({scope: this.gapi.settings.scopes}, function(session, error) {
                    if (callback) {
                        callback(me.isLoggedIn(),error);
                    }
                });
            },
            logout: function(callback) {
                this.gapi.disconnect(callback);
            },
            getLoggedInUser : function() {
                return this.gapi.session ? fromGPPlayerToCocoonUser(this.gapi.session) : null;
            },
            requestUser: function(callback, userId) {
                this.gapi.loadPlayer(userId || "", function(player, error) {
                    if (error) {
                        callback(null, error);
                    }
                    else {
                        callback(fromGPPlayerToCocoonUser(player), null);
                    }
                });
            },
            requestUserImage: function(callback, userID, imageSize) {
                callback(false, {message: "Not implemented"});

            },
            requestFriends: function(callback, userId) {
                callback(false, {message: "Not implemented"});
            },

            publishMessage: function(message, callback) {
                if (callback)
                    callback({message:"Not implemented"});
            },

            publishMessageWithDialog: function(message, callback) {
                if (callback)
                    callback({message:"Not implemented"});
            },

            requestScore: function(callback, params) {
                params = params || {};
                var playerId = params.userID || "me";
                var leaderboardID = params.leaderboardID || this.gapi.settings.defaultLeaderboard;
                if (!leaderboardID)
                    throw "leaderboardID not provided in the params. You can also set the default leaderboard in the init method";


                this.gapi.loadScore(leaderboardID, function(score, error) {
                    if (error) {
                        callback(null, error);
                    }
                    else {
                        callback(new Cocoon.Social.Score(score || 0));
                    }

                });
            },

            submitScore: function(score, callback, params) {
                params = params || {};
                var leaderboardID = params.leaderboardID || this.gapi.settings.defaultLeaderboard;
                if (!leaderboardID)
                    throw "leaderboardID not provided in the params. You can also set the default leaderboard in the init method";
                this.gapi.submitScore(score, leaderboardID, callback);
            },

            showLeaderboard : function(callback, params) {
                params = params || {};
                var leaderboardID = params.leaderboardID || "";
                this.gapi.showLeaderboard(callback, leaderboardID);
            },

            //internal utility function
            prepareAchievements: function(reload, callback) {
                if (!this._cachedAchievements || reload) {
                    var me = this;
                    this.gapi.loadAchievements(function(items, error){
                        if (items && !error) {
                            var achievements = [];
                            for (var i = 0; i < items.length; i++) {
                               achievements.push(fromGPAchievementToCocoonAchievement(items[i]));
                            }
                            me.setCachedAchievements(achievements);
                            callback(achievements, null);
                        }
                        else {
                            callback([], response ? response.error : null);
                        }
                    });
                }
                else {
                    callback(this._cachedAchievements, null);
                }
            },

            requestAllAchievements : function(callback) {
                this.gapi.loadAchievements(function(items, error){
                    if (items && !error) {
                        var achievements = [];
                        for (var i = 0; i < items.length; i++) {
                           achievements.push(fromGPAchievementToCocoonAchievement(items[i]));
                        }
                        callback(achievements, null);
                    }
                    else {
                        callback([], error);
                    }
                });
            },

            requestAchievements : function(callback, userID) {
                this.gapi.loadAchievements(function(items, error){
                    if (items && !error) {
                        var achievements = [];
                        for (var i = 0; i < items.length; i++) {
                           if (items[i].unlocked) {
                              achievements.push(fromGPAchievementToCocoonAchievement(items[i]));
                           }
                        }
                        callback(achievements, null);
                    }
                    else {
                        callback([], error);
                    }
                });
            },
            submitAchievement: function(achievementID, callback) {
                if (achievementID === null || typeof achievementID === 'undefined')
                    throw "No achievementID specified";
                var achID = this.translateAchievementID(achievementID);
                this.gapi.submitAchievement(achID, this.gapi.settings.showAchievementNotifications, callback);
            },
            resetAchievements : function(callback) {
                if (callback) {
                    callback({message:"Not implemented"});
                }
            },
            showAchievements : function(callback) {
                this.gapi.showAchievements(callback);
            }
        };


        Cocoon.extend(Cocoon.Social.SocialServiceGooglePlayGames, Cocoon.Social.SocialGamingService);

        /**
         *@ignore
         */
        function fromGPPlayerToCocoonUser (gpPlayer) {
            return new Cocoon.Social.User(gpPlayer.playerId, gpPlayer.playerAlias, "");
        }

        /**
         *@ignore
         */
        function fromGPAchievementToCocoonAchievement(gpItem) {
            var result = new Cocoon.Social.Achievement (
                gpItem.identifier,
                gpItem.title,
                gpItem.description,
                "",
                0
            );
            result.gpAchievementData = gpItem;
            return result;
        }
        /**
         *@ignore
         */
        function fromGPScoreToCocoonScore(gpItem, leaderboardID) {
            return new Cocoon.Social.Score(gpItem.player.playerId, gpItem.scoreValue, gpItem.player.displayName, gpItem.player.avatarImageUrl, leaderboardID);
        }


        return extension;
    });

})();
