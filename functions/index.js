const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

const db = admin.firestore();

/**
 * ✅ Send notification to admin when new ride is created
 */
exports.sendNewRideNotification = functions
    .region('asia-southeast1')  // ✅ Singapore region
    .firestore
    .document('rides/{rideId}')
    .onCreate(async (snapshot, context) => {
        const ride = snapshot.data();
        const rideId = context.params.rideId;

        console.log(`📋 New ride created: ${rideId}`);
        console.log('📦 Ride data:', JSON.stringify(ride));

        // ✅ Get admin token
        const tokenSnapshot = await db.collection('admin_tokens')
            .where('isActive', '==', true)
            .get();

        if (tokenSnapshot.empty) {
            console.log('❌ No admin tokens found');
            return null;
        }

        // ✅ Build notification payload
        const notification = {
            notification: {
                title: '🚗 New Ride Booked!',
                body: `${ride.vehicleName || 'Vehicle'} | ${ride.pickup?.address?.substring(0, 30) || 'Pickup'}...`,
            },
            data: {
                rideId: rideId,
                pickupAddress: ride.pickup?.address || '',
                destinationAddress: ride.destination?.address || '',
                vehicleName: ride.vehicleName || '',
                totalFare: String(ride.totalFare || 0),
            },
            android: {
                priority: 'high',
                notification: {
                    sound: 'default',
                    channelId: 'admin_notification_channel',
                },
            },
        };

        // ✅ Send to all admin devices
        const promises = [];
        tokenSnapshot.forEach((doc) => {
            const token = doc.data().token;
            if (token) {
                console.log(`📤 Sending notification to: ${token}`);
                promises.push(
                    admin.messaging().send({
                        ...notification,
                        token: token,
                    })
                );
            }
        });

        try {
            const results = await Promise.allSettled(promises);
            const successCount = results.filter(r => r.status === 'fulfilled').length;
            const failCount = results.filter(r => r.status === 'rejected').length;
            console.log(`✅ Notification sent: ${successCount} success, ${failCount} failed`);
        } catch (error) {
            console.error('❌ Error sending notifications:', error);
        }

        return null;
    });

/**
 * ✅ Send notification to admin when ride is completed
 */
exports.sendRideCompletedNotification = functions
    .region('asia-southeast1')  // ✅ Singapore region
    .firestore
    .document('rides/{rideId}')
    .onUpdate(async (change, context) => {
        const beforeData = change.before.data();
        const afterData = change.after.data();
        const rideId = context.params.rideId;

        if (beforeData.status !== 'COMPLETED' && afterData.status === 'COMPLETED') {
            console.log(`✅ Ride completed: ${rideId}`);

            const tokenSnapshot = await db.collection('admin_tokens')
                .where('isActive', '==', true)
                .get();

            if (tokenSnapshot.empty) {
                console.log('❌ No admin tokens found');
                return null;
            }

            const notification = {
                notification: {
                    title: '✅ Ride Completed!',
                    body: `Ride #${rideId.substring(0, 8)} | ₹${afterData.totalFare || 0}`,
                },
                data: {
                    rideId: rideId,
                    status: 'COMPLETED',
                },
                android: {
                    priority: 'high',
                    notification: {
                        sound: 'default',
                        channelId: 'admin_notification_channel',
                    },
                },
            };

            const promises = [];
            tokenSnapshot.forEach((doc) => {
                const token = doc.data().token;
                if (token) {
                    promises.push(
                        admin.messaging().send({
                            ...notification,
                            token: token,
                        })
                    );
                }
            });

            await Promise.allSettled(promises);
            console.log(`✅ Completed notification sent`);
        }

        return null;
    });

/**
 * ✅ Send notification to admin when ride is cancelled
 */
exports.sendRideCancelledNotification = functions
    .region('asia-southeast1')  // ✅ Singapore region
    .firestore
    .document('rides/{rideId}')
    .onUpdate(async (change, context) => {
        const beforeData = change.before.data();
        const afterData = change.after.data();
        const rideId = context.params.rideId;

        if (beforeData.status !== 'CANCELLED' && afterData.status === 'CANCELLED') {
            console.log(`❌ Ride cancelled: ${rideId}`);

            const tokenSnapshot = await db.collection('admin_tokens')
                .where('isActive', '==', true)
                .get();

            if (tokenSnapshot.empty) {
                console.log('❌ No admin tokens found');
                return null;
            }

            const reason = afterData.cancelReason || 'No reason provided';

            const notification = {
                notification: {
                    title: '❌ Ride Cancelled',
                    body: `Ride #${rideId.substring(0, 8)} | Reason: ${reason}`,
                },
                data: {
                    rideId: rideId,
                    status: 'CANCELLED',
                    cancelReason: reason,
                },
                android: {
                    priority: 'high',
                    notification: {
                        sound: 'default',
                        channelId: 'admin_notification_channel',
                    },
                },
            };

            const promises = [];
            tokenSnapshot.forEach((doc) => {
                const token = doc.data().token;
                if (token) {
                    promises.push(
                        admin.messaging().send({
                            ...notification,
                            token: token,
                        })
                    );
                }
            });

            await Promise.allSettled(promises);
            console.log(`✅ Cancelled notification sent`);
        }

        return null;
    });