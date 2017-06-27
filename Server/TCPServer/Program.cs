﻿using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Threading;

namespace TCPServer
{
    class Program
    {
        static void Main(string[] args)
        {
            /* File name for received picture */
            const String FILE_NAME = "Received.jpg";

            /* Create a buffer for receiving */
            byte[] receiveBytes = new byte[1024];

            /* The IPEndPoint for the server. IP cannot be localhost */
            IPEndPoint remoteIpEndPoint = new IPEndPoint(IPAddress.Parse("192.168.1.8"), 3800);

            /* After this amount of time has passed, any connection will be terminated
             * Keep high for high latency networks and vice versa */
            const int TIMEOUT = 1000;

            /* Start listening for connections */
            TcpListener tcpListener = new TcpListener(remoteIpEndPoint);
            tcpListener.Start();

            /* The socket that will be used for listening */
            Socket sock = null;

            /* FileStream for writing */
            FileStream objWriter = null;

            /* Number and total number of bytes read till the end of loop */
            int bytesRead = 0;
            int totalBytesRead = 0;            

            /* Loop till something is read */
            while (totalBytesRead == 0) {

                /* Sleep for 100ms if no connection is being made */
                while (!tcpListener.Pending()) Thread.Sleep(100);

                sock = tcpListener.AcceptSocket();
                Console.WriteLine("Accepted Connection");
                sock.ReceiveTimeout = TIMEOUT;

                /* Sleep for another 100ms to give the client time to respond */
                Thread.Sleep(100);
                int filesize = 0;
                try
                {
                    if ((bytesRead = sock.Receive(receiveBytes)) > 0)
                    {
                        string[] headers = System.Text.Encoding.ASCII.GetString(receiveBytes).Split('\n');
                        if (headers[0] == "HEADER")
                        {
                            Console.WriteLine("Receiving file of size " + headers[1] + " bytes");
                            Int32.TryParse(headers[1], out filesize);
                        }
                        else throw new Exception("No header received");
                    }
                    else throw new Exception("No header received");

                    while ((totalBytesRead != filesize) && (bytesRead = sock.Receive(receiveBytes,receiveBytes.Length, SocketFlags.None )) > 0)
                    {
                        /* Delete existing file to be safe */
                        if (objWriter is null)
                        {
                            if (File.Exists(FILE_NAME)) File.Delete(FILE_NAME);
                            objWriter = File.OpenWrite(FILE_NAME);
                        }

                        objWriter.Write(receiveBytes, 0, bytesRead);

                        totalBytesRead += bytesRead;
                        if(filesize - totalBytesRead < receiveBytes.Length)
                        {
                            receiveBytes = new byte[filesize - totalBytesRead];
                        }
                    }
                }
                catch (Exception e)
                {
                    Console.WriteLine(e.Message);
                }

                /* Close everything */
                sock.Close();
                if (!(objWriter is null))
                {
                    objWriter.Close();
                    objWriter = null;
                }
                Console.WriteLine("Closed Connection");
            }
            /* Clean up and open the received file */
            tcpListener.Stop();
            //Process.Start(FILE_NAME);
            Console.ReadKey();
        }
    }
}
